package fi.kansalliskirjasto.ekirjasto.testing

import android.content.Context
import android.content.Context.MODE_PRIVATE
import fi.ekirjasto.testing.BuildConfig

/**
 * Override values for testing the app.
 */
sealed class TestingOverrides {
  companion object {
    private var initialized = false

    private const val SHARED_PREFS_NAME = "EkirjastoTesting"

    var testLoginActive: Boolean = false
    const val TEST_LOGIN_CIRCULATION_API_URL: String = BuildConfig.TEST_LOGIN_CIRCULATION_API_URL
    const val TEST_LOGIN_LIBRARY_PROVIDER_ID: String = BuildConfig.TEST_LOGIN_LIBRARY_PROVIDER_ID

    fun init(context: Context) {
      if (initialized) {
        return
      }

      val testingPrefs = context.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
      testLoginActive = testingPrefs.getBoolean("testLoginActive", false)

      initialized = true
    }
  }
}
