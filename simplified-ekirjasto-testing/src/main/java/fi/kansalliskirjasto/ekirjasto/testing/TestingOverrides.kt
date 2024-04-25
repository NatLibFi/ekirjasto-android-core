package fi.kansalliskirjasto.ekirjasto.testing

import fi.ekirjasto.testing.BuildConfig

/**
 * Override values for testing the app.
 */
sealed class TestingOverrides {
  companion object {
    var testLoginActive: Boolean = false
    const val TEST_LOGIN_CIRCULATION_API_URL: String = BuildConfig.TEST_LOGIN_CIRCULATION_API_URL
    const val TEST_LOGIN_LIBRARY_PROVIDER_ID: String = BuildConfig.TEST_LOGIN_LIBRARY_PROVIDER_ID
  }
}
