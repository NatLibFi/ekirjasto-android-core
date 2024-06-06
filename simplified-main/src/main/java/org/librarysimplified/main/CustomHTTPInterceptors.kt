package org.librarysimplified.main

import android.content.Context
import android.content.res.Resources
import fi.kansalliskirjasto.ekirjasto.util.LocaleHelper
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
      return AcceptLanguage(context)
    }
  }

  class AcceptLanguage(cont: Context) : Interceptor {
    private val logger = LoggerFactory.getLogger(AcceptLanguage::class.java)
    private val context = cont

    override fun intercept(chain: Interceptor.Chain): Response {
      val acceptLanguageHeader = getAcceptLanguageHeader()
      logger.debug("Insert Accept-Language header: $acceptLanguageHeader")
      val request = chain.request().newBuilder()
        .addHeader("Accept-Language", acceptLanguageHeader)
        .build()
      return chain.proceed(request)
    }

    private fun getAcceptLanguageHeader(): String {
      //Get current language from LocaleHelper
      val languageTag = LocaleHelper.getLanguage(context)
      // According to RFC2616, quality must be in the range 0-1 and have at most 3 decimal digits,
      // so let's start at 1.000
      val quality = 1.000
      val acceptLanguageHeader ="$languageTag;q=" + String.format(Locale.getDefault(), "%.3f", quality)
      return acceptLanguageHeader
    }
  }
}
