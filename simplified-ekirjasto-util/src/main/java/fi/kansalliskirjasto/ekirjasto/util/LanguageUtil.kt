package fi.kansalliskirjasto.ekirjasto.util

import android.content.res.Resources
import fi.ekirjasto.util.BuildConfig
import java.net.URI
import java.net.URL

/**
 * Language and localization related utilities.
 */
sealed class LanguageUtil {
  companion object {
    /**
     * Get the user's language.
     */
    fun getUserLanguage(): String {
      // Get the list of languages supported by the app
      val appLanguages = BuildConfig.LANGUAGES.split(",")
      // Get the list of languages that the user has set for their device
      // LocaleList cannot give you an actual *list*, so convert to a string and split into a list
      val userLanguages = Resources.getSystem().configuration.locales.toLanguageTags().split(",")
        // Get only the language from the language tag (e.g. "en" from "en-US")
        .map { it.split("-")[0] }
      // Get the first user language that is one of the app's supported languages, or default to "en"
      return userLanguages.firstOrNull{ appLanguages.contains(it) } ?: "en"
    }

    /**
     * Insert the user's language to a URI.
     */
    fun insertLanguageInURL(url: URL): URL {
      val language = getUserLanguage()
      val urlString = url.toString().replace("__LANGUAGE__", language)
      return URI.create(urlString).toURL()
    }
  }
}
