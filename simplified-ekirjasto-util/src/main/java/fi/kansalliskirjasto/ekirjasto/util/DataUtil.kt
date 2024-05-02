package fi.kansalliskirjasto.ekirjasto.util

import android.app.ActivityManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import com.jakewharton.processphoenix.ProcessPhoenix
import org.slf4j.LoggerFactory
import java.io.File

/**
 * App data related utilities.
 */
sealed class DataUtil {
  companion object {
    private val logger = LoggerFactory.getLogger(DataUtil::class.java)

    fun clearAppDataAndExit(context: Context) {
      logger.warn("clearAppDataAndExit()")
      (context.getSystemService(ACTIVITY_SERVICE) as ActivityManager).clearApplicationUserData()
    }

    fun deleteEverythingExceptSharedPrefs(context: Context) {
      logger.warn("deleteEverythingExceptSharedPrefs()")
      val basePath = context.filesDir.parent!! + File.separator
      deleteRecursively(File(basePath), "shared_prefs")
      //deleteRecursively(File("${basePath}cache"))
      //deleteRecursively(File("${basePath}databases"))
      //deleteRecursively(File("${basePath}files"))
      //deleteRecursively(File("${basePath}no_backup"))
    }

    fun restartApp(context: Context, delay: Long = 0L) {
      logger.warn("restartApp()")
      ProcessPhoenix.triggerRebirth(context)
    }

    private fun deleteRecursively(fileOrDirectory: File, ignore: String? = null) {
      if (ignore != null && fileOrDirectory.path.contains(ignore)) {
        return
      }

      logger.debug("deleteRecursivelyInner({})", fileOrDirectory)

      if (fileOrDirectory.isDirectory()) {
        for (child in fileOrDirectory.listFiles() ?: arrayOf()) {
          deleteRecursively(child, ignore)
        }
      }

      fileOrDirectory.delete()
    }
  }
}
