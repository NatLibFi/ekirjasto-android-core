package org.librarysimplified.main

import android.content.Context
import android.content.res.Resources
import okhttp3.Interceptor
import okhttp3.Response
import org.librarysimplified.http.vanilla.extensions.LSHTTPInterceptorFactoryType
import org.slf4j.LoggerFactory
import java.util.Locale
import kotlin.math.max


class CustomHTTPInterceptors {
  class AcceptLanguageFactory : LSHTTPInterceptorFactoryType {
    override val name: String = "org.librarysimplified.http.accept_language"
    override val version: String = "1.0.0"

    override fun createInterceptor(context: Context): Interceptor {
      return AcceptLanguage()
    }
  }

  class AcceptLanguage : Interceptor {
    private val logger = LoggerFactory.getLogger(AcceptLanguage::class.java)

    override fun intercept(chain: Interceptor.Chain): Response {
      val acceptLanguageHeader = getAcceptLanguageHeader()
      logger.debug("Insert Accept-Language header: $acceptLanguageHeader")
      val request = chain.request().newBuilder()
        .addHeader("Accept-Language", acceptLanguageHeader)
        .build()
      return chain.proceed(request)
    }

    private fun getAcceptLanguageHeader(): String {
      //Get current language from Locale, the current language being .getDefault()
      val languageTag = Locale.getDefault().language
      // According to RFC2616, quality must be in the range 0-1 and have at most 3 decimal digits,
      // so let's start at 1.000
      var quality = 1.000
      val acceptLanguageHeader ="$languageTag;q=" + String.format(Locale.ENGLISH, "%.3f", quality)


      // LocaleList cannot give you an actual *list*, so convert to a string and split into a list
      //val languageTags = Resources.getSystem().configuration.locales.toLanguageTags().split(",")
      // According to RFC2616, quality must be in the range 0-1 and have at most 3 decimal digits,
      // so let's start at 1.000 and go down by 0.001 after every language tag
      //var quality = 1.000
      //val acceptLanguageHeader = languageTags.fold(""){ accumulator, langTag ->
        // Java (or Kotlin here) is always so *succinct*
      //  val langTagWithQ = "$langTag;q=" + String.format(Locale.ENGLISH, "%.3f", quality)
        quality = max(0.001, quality - 0.001)
      //  if (accumulator.isEmpty()) langTagWithQ else "$accumulator, $langTagWithQ"
      //}
      return acceptLanguageHeader //return acceptLanguageHeader
    }
  }
}
