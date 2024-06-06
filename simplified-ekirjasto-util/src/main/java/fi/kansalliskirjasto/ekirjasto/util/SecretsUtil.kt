package fi.kansalliskirjasto.ekirjasto.util

import android.util.Base64
import android.util.Log
import fi.ekirjasto.util.BuildConfig

/**
 * Secrets related utilities.
 *
 * The purpose of this class is to obfuscate the secrets used in the app,
 * although it's not really possible to completely hide client-side secrets.
 *
 * TODO: Use something more complex than just base64 encoding here.
 */
sealed class SecretsUtil {
  companion object {
    /**
     * Decode a base64 encoded string
     */
    private fun base64Decode(input: String): String {
      if (input.isBlank()) {
        return ""
      }

      val data = Base64.decode(input, Base64.DEFAULT)
      return String(data, Charsets.UTF_8)
    }

    /**
     * Get the test login PIN code.
     */
    fun getTestLoginPin(): String {
      return base64Decode(BuildConfig.TEST_LOGIN_PIN_BASE64)
    }

    /**
     * Get the Transifex token.
     */
    fun getTransifexToken(): String {
      return base64Decode(BuildConfig.TRANSIFEX_TOKEN_BASE64)
    }
  }
}
