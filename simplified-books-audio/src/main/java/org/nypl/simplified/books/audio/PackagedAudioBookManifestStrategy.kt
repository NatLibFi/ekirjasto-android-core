package org.nypl.simplified.books.audio

import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfilled
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentError
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.slf4j.LoggerFactory
import java.util.zip.ZipFile

/**
 * An audio book manifest strategy that extracts the manifest from a downloaded audio book file.
 */

class PackagedAudioBookManifestStrategy(
  private val request: AudioBookManifestRequest
) : AbstractAudioBookManifestStrategy(request) {

  private val logger =
    LoggerFactory.getLogger(PackagedAudioBookManifestStrategy::class.java)

  override fun fulfill(
    taskRecorder: TaskRecorderType
  ): PlayerResult<ManifestFulfilled, ManifestFulfillmentError> {
    taskRecorder.beginNewStep("Extracting manifest…")

    return this.extractManifest()
  }

  /**
   * Attempt to synchronously extract a manifest file from the audio book package.
   *
   * Audio book packages are ZIP archives in which the manifest entry is always stored
   * in cleartext (only the audio resources may be LCP-encrypted). We open the archive
   * with java.util.zip directly rather than via a Readium archive abstraction; the
   * Readium 3.x equivalents (ArchiveOpener / ZipArchiveOpener) are heavier and would
   * couple this strategy unnecessarily to the Readium asset retrieval pipeline.
   */

  private fun extractManifest(): PlayerResult<ManifestFulfilled, ManifestFulfillmentError> {
    if (this.request.file == null) {
      return PlayerResult.Failure(extractFailed("No audio book file"))
    }

    val manifestEntryPath = this.request.targetURI.toString().trimStart('/')
    val filePath = this.request.file.absolutePath

    this.logger.debug("extractManifest: extracting {} from {}", manifestEntryPath, filePath)

    val manifestBytes = try {
      ZipFile(filePath).use { zip ->
        val entry = zip.getEntry(manifestEntryPath) ?: return@use null
        zip.getInputStream(entry).use { it.readBytes() }
      }
    } catch (e: Exception) {
      this.logger.debug("extractManifest: failed to read manifest entry: ", e)
      null
    }

    return if (manifestBytes == null) {
      PlayerResult.Failure(extractFailed("Unable to extract manifest from audio book file"))
    } else {
      PlayerResult.unit(
        ManifestFulfilled(
          source = this.request.targetURI,
          contentType = this.request.contentType,
          authorization = null,
          data = manifestBytes
        )
      )
    }
  }

  private fun extractFailed(
    message: String
  ): ManifestFulfillmentError {
    return ManifestFulfillmentError(
      message = message,
      extraMessages = listOf(),
      serverData = null
    )
  }
}
