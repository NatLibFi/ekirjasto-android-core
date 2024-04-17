package org.librarysimplified.main

import android.content.Context
import com.transifex.txnative.LocaleState
import com.transifex.txnative.TxNative
import com.transifex.txnative.missingpolicy.WrappedStringPolicy
import org.slf4j.LoggerFactory
import java.io.FileNotFoundException
import java.util.Properties

/**
 * Functions to enable Transifex string translation.
 */

object MainTransifex {

  private val logger = LoggerFactory.getLogger(MainTransifex::class.java)

  private fun loadTransifexToken(context: Context): String? {
    return try {
      context.assets.open("secrets.conf").use { stream ->
        val props = Properties()
        props.load(stream)
        props.getProperty("transifex.token")
      }
    } catch (e: FileNotFoundException) {
      this.logger.warn("secrets.conf not found, will insert empty Transifex token")
      null
    } catch (e: Exception) {
      this.logger.warn("Could not find Transifex token in secrets.conf, will insert empty token", e)
      null
    }
  }

  /**
   * Configure Transifex.
   *
   * Will insert an empty token for Transifex if a token is not found in assets.
   */

  fun configure(applicationContext: Context) {
    this.logger.debug("MainTransifex.configure()")
    val token = loadTransifexToken(applicationContext) ?: ""

    val languages = BuildConfig.LANGUAGES.split(",")
    this.logger.debug("Languages: " + languages.joinToString(", "))

    val localeState =
      LocaleState(
        applicationContext,
        languages.first(),
        languages.toTypedArray(),
        null
      )

    val stringPolicy =
      if (BuildConfig.DEBUG) {
        WrappedStringPolicy("[[", "]]")
      } else {
        WrappedStringPolicy(null, null)
      }

    this.logger.debug("Initializing Transifex")
    TxNative.init(
      applicationContext,
      localeState,
      token,
      null,
      null,
      stringPolicy
    )

    this.logger.debug("Retrieving Transifex string translations")
    TxNative.fetchTranslations(null, null)
  }
}
