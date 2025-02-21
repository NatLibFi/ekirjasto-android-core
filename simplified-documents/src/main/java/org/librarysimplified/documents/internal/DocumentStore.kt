package org.librarysimplified.documents.internal

import android.content.res.AssetManager
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import org.librarysimplified.documents.DocumentConfiguration
import org.librarysimplified.documents.DocumentConfigurationServiceType
import org.librarysimplified.documents.DocumentStoreType
import org.librarysimplified.documents.DocumentType
import org.librarysimplified.documents.EULAType
import org.librarysimplified.http.api.LSHTTPClientType
import org.slf4j.LoggerFactory
import java.io.File

internal class DocumentStore private constructor(
  override val about: DocumentType?,
  override val acknowledgements: DocumentType?,
  override val eula: EULAType?,
  override val licenses: DocumentType?,
  override val privacyPolicy: DocumentType?,
  override val feedback: DocumentType?,
  override val accessibilityStatement: DocumentType?,
  override val instructionsEN: DocumentType?,
  override val instructionsFI: DocumentType?,
  override val instructionsSV: DocumentType?,
  override val faq: DocumentType?
) : DocumentStoreType {

  companion object {

    private val logger =
      LoggerFactory.getLogger(DocumentStore::class.java)

    fun create(
      assetManager: AssetManager,
      http: LSHTTPClientType,
      baseDirectory: File,
      configuration: DocumentConfigurationServiceType
    ): DocumentStoreType {
      val about =
        this.documentForMaybe(
          assetManager = assetManager,
          http = http,
          baseDirectory = baseDirectory,
          config = configuration.about
        )

      val acknowledgements =
        this.documentForMaybe(
          assetManager = assetManager,
          http = http,
          baseDirectory = baseDirectory,
          config = configuration.acknowledgements
        )

      val eula =
        this.eulaForMaybe(
          assetManager = assetManager,
          http = http,
          baseDirectory = baseDirectory,
          config = configuration.eula
        )

      val licenses =
        this.documentForMaybe(
          assetManager = assetManager,
          http = http,
          baseDirectory = baseDirectory,
          config = configuration.licenses
        )

      val privacyPolicy =
        this.documentForMaybe(
          assetManager = assetManager,
          http = http,
          baseDirectory = baseDirectory,
          config = configuration.privacyPolicy
        )

      val feedback =
        this.documentForMaybe(
          assetManager = assetManager,
          http = http,
          baseDirectory = baseDirectory,
          config = configuration.feedback
        )

      val accessibilityStatement =
        this.documentForMaybe(
          assetManager = assetManager,
          http = http,
          baseDirectory = baseDirectory,
          config = configuration.accessibilityStatement
        )

      val instructionsFI =
        this.documentForMaybe(
          assetManager = assetManager,
          http = http,
          baseDirectory = baseDirectory,
          config = configuration.instructionsFI
        )
      val instructionsSV =
        this.documentForMaybe(
          assetManager = assetManager,
          http = http,
          baseDirectory = baseDirectory,
          config = configuration.instructionsSV
        )
      val instructionsEN =
        this.documentForMaybe(
          assetManager = assetManager,
          http = http,
          baseDirectory = baseDirectory,
          config = configuration.instructionsEN
        )

      val faq =
        this.documentForMaybe(
          assetManager = assetManager,
          http = http,
          baseDirectory = baseDirectory,
          config = configuration.faq
        )

      return DocumentStore(
        about = about,
        acknowledgements = acknowledgements,
        eula = eula,
        licenses = licenses,
        privacyPolicy = privacyPolicy,
        feedback = feedback,
        accessibilityStatement = accessibilityStatement,
        instructionsEN = instructionsEN,
        instructionsFI = instructionsFI,
        instructionsSV = instructionsSV,
        faq = faq
      )
    }

    private fun eulaForMaybe(
      assetManager: AssetManager,
      http: LSHTTPClientType,
      baseDirectory: File,
      config: DocumentConfiguration?
    ): EULAType? {
      return config?.let {
        this.eulaFor(assetManager, http, baseDirectory, config)
      }
    }

    private fun eulaFor(
      assetManager: AssetManager,
      http: LSHTTPClientType,
      baseDirectory: File,
      config: DocumentConfiguration
    ): EULAType {
      this.logger.debug("eula {} ({})", config.name, config.remoteURI)

      return if (!config.name.isNullOrBlank()) {
        EULA(
          http = http,
          initialStreams = {
            assetManager.open(config.name)
          },
          file = File(baseDirectory, config.name),
          fileTmp = File(baseDirectory, config.name + ".tmp"),
          fileAgreed = File(baseDirectory, "eula_agreed.dat"),
          remoteURL = config.remoteURI.toURL()
        )
      } else {
        EULA(
          http = http,
          initialStreams = null,
          file = null,
          fileTmp = null,
          fileAgreed = File(baseDirectory, "eula_agreed.dat"),
          remoteURL = config.remoteURI.toURL()
        )
      }
    }

    private fun documentForMaybe(
      assetManager: AssetManager,
      http: LSHTTPClientType,
      baseDirectory: File,
      config: DocumentConfiguration?
    ): DocumentType? {
      return config?.let {
        this.documentFor(assetManager, http, baseDirectory, config)
      }
    }

    private fun documentFor(
      assetManager: AssetManager,
      http: LSHTTPClientType,
      baseDirectory: File,
      config: DocumentConfiguration
    ): DocumentType {
      this.logger.debug("plain document {} ({})", config.name, config.remoteURI)

      return if (!config.name.isNullOrBlank()) {
        PlainDocument(
          http = http,
          initialStreams = {
            assetManager.open(config.name)
          },
          file = File(baseDirectory, config.name),
          fileTmp = File(baseDirectory, config.name + ".tmp"),
          remoteURL = config.remoteURI.toURL()
        )
      } else {
        PlainDocument(
          http = http,
          initialStreams = null,
          file = null,
          fileTmp = null,
          remoteURL = config.remoteURI.toURL()
        )
      }
    }
  }

  override fun update(executor: ListeningExecutorService): ListenableFuture<*> {
    return Futures.allAsList(
      listOf(
        this.updateOne(executor, this.about),
        this.updateOne(executor, this.acknowledgements),
        this.updateOne(executor, this.eula),
        this.updateOne(executor, this.licenses),
        this.updateOne(executor, this.privacyPolicy)
      )
    )
  }

  private fun updateOne(
    executor: ListeningExecutorService,
    document: DocumentType?
  ): ListenableFuture<*> {
    return executor.submit {
      try {
        document?.update()
      } catch (e: Throwable) {
        logger.debug("unable to update document: ", e)
      }
    }
  }
}
