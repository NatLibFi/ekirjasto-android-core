package org.librarysimplified.viewer.audiobook

import android.app.Activity
import android.content.Intent
import one.irradia.mime.api.MIMEType
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckProviderType
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfilled
import org.librarysimplified.audiobook.manifest_parser.extension_spi.ManifestParserExtensionType
import org.librarysimplified.audiobook.views.PlayerModel
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.services.api.Services
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.api.playerCredentials
import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.viewer.spi.ViewerPreferences
import org.nypl.simplified.viewer.spi.ViewerProviderType
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.ServiceLoader
import java.util.UUID

/**
 * An audio book viewer service.
 *
 * As of audiobook 24.0.0, manifest fulfillment, license checking, and engine selection are owned
 * by the global [PlayerModel]. This viewer hands the (already downloaded) packaged audiobook — or,
 * for older installs, the stored manifest — to the model and then opens the player activity, which
 * observes the model's state.
 */

class AudioBookViewer : ViewerProviderType {

  private val logger =
    LoggerFactory.getLogger(AudioBookViewer::class.java)

  override val name: String =
    "org.librarysimplified.viewer.audiobook.AudioBookViewer"

  override fun canSupport(
    preferences: ViewerPreferences,
    book: Book,
    format: BookFormat
  ): Boolean {
    return when (format) {
      is BookFormat.BookFormatEPUB,
      is BookFormat.BookFormatPDF -> {
        this.logger.debug("audio book viewer can only view audio books")
        false
      }
      is BookFormat.BookFormatAudioBook ->
        true
    }
  }

  override fun canPotentiallySupportType(type: MIMEType): Boolean {
    return StandardFormatNames.allAudioBooks.contains(type)
  }

  override fun open(
    activity: Activity,
    preferences: ViewerPreferences,
    book: Book,
    format: BookFormat,
    accountProviderId: URI
  ) {
    val services =
      Services.serviceDirectory()
    val httpClient =
      services.requireService(LSHTTPClientType::class.java)
    val parserExtensions =
      ServiceLoader.load(ManifestParserExtensionType::class.java).toList()
    val licenseChecks =
      ServiceLoader.load(SingleLicenseCheckProviderType::class.java).toList()

    val formatAudio =
      format as BookFormat.BookFormatAudioBook
    val file =
      formatAudio.file
    val manifest =
      formatAudio.manifest
    val drmInformation =
      formatAudio.drmInformation

    PlayerModel.start(activity.application)
    PlayerModel.bookTitle = book.entry.title
    PlayerModel.bookAuthor = book.entry.authors.firstOrNull() ?: ""

    val palaceID =
      PlayerPalaceID(book.entry.id)
    val bookCredentials =
      drmInformation.playerCredentials()

    AudioBookViewerModel.parameters =
      AudioBookPlayerParameters(
        accountID = book.account,
        accountProviderID = accountProviderId,
        bookID = book.id,
        drmInfo = drmInformation,
        opdsEntry = book.entry,
        playerID = UUID.randomUUID()
      )

    /*
     * e-kirjasto audiobooks are downloaded as packaged (LCP) files. Hand the packaged file to
     * the player model, which parses the manifest, performs the license checks, and decrypts the
     * audio using the supplied credentials.
     */

    if (file != null) {
      this.logger.debug("opening packaged audiobook from {}", file)
      PlayerModel.downloadLocalPackagedAudiobook(
        context = activity.application,
        bookCredentials = bookCredentials,
        bookFile = file,
        cacheDir = activity.cacheDir,
        licenseChecks = licenseChecks,
        palaceID = palaceID,
        parserExtensions = parserExtensions,
        httpClient = httpClient
      )
      this.openActivity(activity)
      return
    }

    /*
     * Otherwise (older installs), open the player using the stored manifest file.
     */

    if (manifest != null) {
      this.logger.debug("opening audiobook from stored manifest {}", manifest.manifestFile)
      PlayerModel.parseAndCheckManifest(
        bookCredentials = bookCredentials,
        cacheDir = activity.cacheDir,
        licenseChecks = licenseChecks,
        httpClient = httpClient,
        manifest = ManifestFulfilled(
          source = null,
          contentType = format.contentType,
          authorization = null,
          data = manifest.manifestFile.readBytes()
        ),
        palaceID = palaceID,
        parserExtensions = parserExtensions
      )
      this.openActivity(activity)
      return
    }

    this.logger.error("audiobook has neither a packaged file nor a manifest; cannot open")
  }

  private fun openActivity(activity: Activity) {
    activity.startActivity(Intent(activity, AudioBookPlayerActivity::class.java))
  }
}
