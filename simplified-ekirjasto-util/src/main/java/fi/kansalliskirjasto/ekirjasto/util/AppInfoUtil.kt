package fi.kansalliskirjasto.ekirjasto.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.MODE_PRIVATE
import fi.ekirjasto.util.BuildConfig
import org.slf4j.LoggerFactory

/**
 * App info related utilities.
 */
sealed class AppInfoUtil {
  companion object {
    private val logger = LoggerFactory.getLogger(AppInfoUtil::class.java)

    private var initialized = false

    private const val SHARED_PREFS_NAME = "EkirjastoAppInfo"

    var hasBuildFlavorChanged = false
      private set

    val buildFlavor: String
      get() {
        return BuildConfig.FLAVOR
      }

    fun init(context: Context) {
      if (initialized) {
        return
      }

      val previousBuildFlavor = getSavedBuildFlavor(context)
      hasBuildFlavorChanged =
        (previousBuildFlavor != null)
          && (buildFlavor != previousBuildFlavor)
      logger.info("Current build flavor:  $buildFlavor")
      logger.info("Previous build flavor: $previousBuildFlavor")
      logger.info("Build flavor changed:  $hasBuildFlavorChanged")

      saveBuildFlavor(context)

      initialized = true
    }

    private fun getSavedBuildFlavor(context: Context): String? {
      val prefs = context.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
      return prefs.getString("buildFlavor", null)
    }
    
    @SuppressLint("ApplySharedPref")
    private fun saveBuildFlavor(context: Context) {
      val prefs = context.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
      val prefsEditor = prefs.edit()
      prefsEditor.putString("buildFlavor", buildFlavor)
      prefsEditor.commit()
    }
  }
}
