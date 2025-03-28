package org.librarysimplified.main

import android.app.Application
import android.content.Context
import android.net.http.HttpResponseCache
import android.os.Process
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import androidx.core.content.pm.PackageInfoCompat
import fi.kansalliskirjasto.ekirjasto.testing.TestingOverrides
import fi.kansalliskirjasto.ekirjasto.util.AppInfoUtil
import fi.kansalliskirjasto.ekirjasto.util.DataUtil
import fi.kansalliskirjasto.ekirjasto.util.LocaleHelper
import io.reactivex.Observable
import org.librarysimplified.services.api.ServiceDirectoryType
import org.nypl.simplified.boot.api.BootEvent
import org.nypl.simplified.boot.api.BootLoader
import org.nypl.simplified.boot.api.BootProcessType
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException

class MainApplication : Application() {

  companion object {
    private lateinit var INSTANCE: MainApplication

    @JvmStatic
    val application: MainApplication
      get() = this.INSTANCE
  }

  private val logger = LoggerFactory.getLogger(MainApplication::class.java)
  private val boot: BootLoader<ServiceDirectoryType> =
    BootLoader(
      bootProcess = object : BootProcessType<ServiceDirectoryType> {
        override fun execute(onProgress: (BootEvent) -> Unit): ServiceDirectoryType {
          return MainServices.setup(this@MainApplication, onProgress)
        }
      },
      bootStringResources = ::MainServicesStrings
    )
  override fun attachBaseContext(base: Context?) {
    super.attachBaseContext(LocaleHelper.onAttach(base,"fi"))
  }
  override fun onCreate() {
    super.onCreate()

    INSTANCE = this
    MainLogging.configure(cacheDir)

    AppInfoUtil.init(this)
    TestingOverrides.init(this)

    if (AppInfoUtil.hasBuildFlavorChanged) {
      logger.error("Build flavor has changed, clearing app data and restarting")
      DataUtil.deleteEverythingExceptSharedPrefs(this)
      DataUtil.restartApp(this)
      return
    }

    if (TestingOverrides.testLoginActive) {
      logger.warn("Test login is active")
    }

    this.configureHttpCache()
    this.configureStrictMode()
    this.logStartup()
    MainTransifex.configure(this.applicationContext)
    this.boot.start(this)
  }

  private fun logStartup() {
    this.logger.debug("starting app: pid {}", Process.myPid())
    this.logger.debug("app version: {}", BuildConfig.SIMPLIFIED_VERSION)
    this.logger.debug("app build:   {}", versionCode())
    this.logger.debug("app commit:  {}", BuildConfig.SIMPLIFIED_GIT_COMMIT)
  }

  private fun versionCode(): String {
    return try {
      val info = this.packageManager.getPackageInfo(this.packageName, 0)
      PackageInfoCompat.getLongVersionCode(info).toString()
    } catch (e: Exception) {
      this.logger.error("version info unavailable: ", e)
      "UNKNOWN"
    }
  }

  /**
   * Install a global HTTP cache.
   */

  private fun configureHttpCache() {
    if (BuildConfig.DEBUG) {
      val httpCacheDir = File(cacheDir, "http")
      val httpCacheSize = 10 * 1024 * 1024.toLong() // 10 MiB
      try {
        HttpResponseCache.install(httpCacheDir, httpCacheSize)
        this.logger.debug("Installed HTTP cache to {}", httpCacheDir)
      } catch (e: IOException) {
        this.logger.warn("Failed to install HTTP cache!", e)
      }
    }
  }

  /**
   * StrictMode is a developer tool which detects things you might be doing by accident and
   * brings them to your attention so you can fix them.
   *
   * StrictMode is most commonly used to catch accidental disk or network access on the
   * application's main thread, where UI operations are received and animations take place.
   */

  private fun configureStrictMode() {
    if (BuildConfig.DEBUG && BuildConfig.ENABLE_STRICT_MODE) {
      StrictMode.setThreadPolicy(
        ThreadPolicy.Builder()
          .detectDiskReads()
          .detectDiskWrites()
          .detectNetwork()
          .penaltyLog()
          .build()
      )
      StrictMode.setVmPolicy(
        VmPolicy.Builder()
          .detectLeakedSqlLiteObjects()
          .detectLeakedClosableObjects()
          .penaltyLog()
          .build()
      )
    }
  }

  /**
   * An observable value that publishes events as the application is booting.
   */

  val servicesBootEvents: Observable<BootEvent>
    get() = this.boot.events
}
