package org.librarysimplified.main

import android.content.Context
import com.transifex.txnative.LocaleState
import com.transifex.txnative.TxNative
import com.transifex.txnative.missingpolicy.WrappedStringPolicy
import fi.kansalliskirjasto.ekirjasto.util.SecretsUtil
import java.io.FileNotFoundException
import java.util.Properties
import org.slf4j.LoggerFactory


/**
 * Functions to enable Transifex string translation.
 */

object MainTransifex {
  private val logger = LoggerFactory.getLogger(MainTransifex::class.java)

  /**
   * Configure Transifex.
   *
   * Will warn about an empty token for Transifex, if not set.
   */
  @Suppress("KotlinConstantConditions")
  fun configure(applicationContext: Context) {
    this.logger.debug("MainTransifex.configure()")
    val transifexToken = SecretsUtil.getTransifexToken()
    if (transifexToken.isBlank()) {
      logger.warn("Transifex token not set, Transifex will only use cached localizations")
    }

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
      transifexToken,
      null,
      null,
      stringPolicy
    )

    this.logger.debug("Retrieving Transifex string translations")
    TxNative.fetchTranslations(null, null)
  }
}
