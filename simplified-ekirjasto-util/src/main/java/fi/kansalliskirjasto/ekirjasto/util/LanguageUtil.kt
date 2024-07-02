package fi.kansalliskirjasto.ekirjasto.util

import android.content.Context
import android.content.res.Resources
import fi.ekirjasto.util.BuildConfig
import java.net.URI
import java.net.URL
import java.util.Locale

/**
 * Language and localization related utilities.
 */
sealed class LanguageUtil {
  companion object {
    /**
     * Get the user's language.
     */
    fun getUserLanguage(context: Context): String {
      //Get user language from LocaleHelper
      val userLanguage = LocaleHelper.getLanguage(context)
      return userLanguage
    }

    /**
     * Insert the user's language to a URI.
     */
    fun insertLanguageInURL(url: URL, context: Context): URL {
      val language = LocaleHelper.getLanguage(context)
      val urlString = url.toString().replace("__LANGUAGE__", language)
      return URI.create(urlString).toURL()
    }
  }
}
