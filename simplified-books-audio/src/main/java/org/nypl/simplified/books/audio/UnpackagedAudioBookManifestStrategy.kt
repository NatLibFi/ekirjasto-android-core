package org.nypl.simplified.books.audio

import org.librarysimplified.audiobook.api.PlayerAuthorizationHandlerType
import org.librarysimplified.audiobook.api.PlayerDownloadRequest
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicParameters
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicType
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAManifestFulfillmentStrategyProviderType
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAManifestURI
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAParameters
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAPassword
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAUsernamePassword
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfilled
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentError
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentEvent
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentStrategyType
import org.librarysimplified.http.api.LSHTTPAuthorizationBasic
import org.librarysimplified.http.api.LSHTTPAuthorizationBearerToken
import org.librarysimplified.http.api.LSHTTPAuthorizationType
import org.librarysimplified.http.api.LSHTTPClientType
import org.nypl.simplified.books.book_database.api.BookFormats
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.slf4j.LoggerFactory

/**
 * An audio book manifest strategy that downloads the manifest from a URI, or loads a fallback if
 * the network is unavailable.
 */

class UnpackagedAudioBookManifestStrategy(
  private val request: AudioBookManifestRequest
) : AbstractAudioBookManifestStrategy(request) {

  private val logger =
    LoggerFactory.getLogger(UnpackagedAudioBookManifestStrategy::class.java)

  override fun fulfill(
    taskRecorder: TaskRecorderType
  ): PlayerResult<ManifestFulfilled, ManifestFulfillmentError> {
    return if (this.request.isNetworkAvailable()) {
      taskRecorder.beginNewStep("Downloading manifest…")
      this.downloadManifest()
    } else {
      taskRecorder.beginNewStep("Loading manifest…")
      this.loadFallbackManifest()
    }
  }

  private fun dataLoadFailed(
    message: String
  ): ManifestFulfillmentError {
    return ManifestFulfillmentError(
      message = message,
      extraMessages = listOf(),
      serverData = null
    )
  }

  private fun loadFallbackManifest(): PlayerResult<ManifestFulfilled, ManifestFulfillmentError> {
    this.logger.debug("loadFallbackManifest")
    return try {
      val data = this.request.loadFallbackData()
      if (data == null) {
        PlayerResult.Failure(dataLoadFailed("No fallback manifest data is provided"))
      } else {
        PlayerResult.unit(data)
      }
    } catch (e: Exception) {
      this.logger.error("loadFallbackManifest: ", e)
      PlayerResult.Failure(dataLoadFailed(e.message ?: e.javaClass.name))
    }
  }

  /**
   * @return `true` if the request content type implies an Overdrive audio book
   */

  private fun isOverdrive(): Boolean {
    return BookFormats.audioBookOverdriveMimeTypes()
      .map { it.fullType }
      .contains(this.request.contentType.fullType)
  }

  /**
   * Attempt to synchronously download a manifest file. If the download fails, return the
   * error details.
   */

  private fun downloadManifest(): PlayerResult<ManifestFulfilled, ManifestFulfillmentError> {
    this.logger.debug("downloadManifest")

    val strategy: ManifestFulfillmentStrategyType =
      this.downloadStrategyForCredentials()
    val fulfillSubscription =
      strategy.events.subscribe(this::onManifestFulfillmentEvent)

    try {
      return strategy.execute()
    } finally {
      fulfillSubscription.dispose()
    }
  }

  /**
   * Try to find an appropriate fulfillment strategy based on the audio book request.
   */

  private fun downloadStrategyForCredentials(): ManifestFulfillmentStrategyType {
    val httpClient =
      this.request.services.requireService(LSHTTPClientType::class.java)
    val authorizationHandler =
      this.authorizationHandlerFor(this.request.credentials)

    return if (this.isOverdrive()) {
      this.logger.debug("downloadStrategyForCredentials: trying an Overdrive strategy")

      val secretService =
        this.request.services.optionalService(
          AudioBookOverdriveSecretServiceType::class.java
        ) ?: throw UnsupportedOperationException("No Overdrive secret service is available")

      val strategies =
        this.request.strategyRegistry.findStrategy(
          OPAManifestFulfillmentStrategyProviderType::class.java
        ) ?: throw UnsupportedOperationException("No Overdrive fulfillment strategy is available")

      strategies.create(
        OPAParameters(
          authorizationHandler = authorizationHandler,
          clientKey = secretService.clientKey,
          clientPass = secretService.clientPass,
          targetURI = OPAManifestURI.Indirect(this.request.targetURI),
          httpClient = httpClient
        )
      )
    } else {
      this.logger.debug("downloadStrategyForCredentials: trying a Basic strategy")

      val strategies =
        this.request.strategyRegistry.findStrategy(
          ManifestFulfillmentBasicType::class.java
        ) ?: throw UnsupportedOperationException("No Basic fulfillment strategy is available")

      strategies.create(
        ManifestFulfillmentBasicParameters(
          uri = this.request.targetURI,
          authorizationHandler = authorizationHandler,
          httpClient = httpClient
        )
      )
    }
  }

  /**
   * As of audiobook 24.0.0, manifest fulfillment obtains credentials through a
   * [PlayerAuthorizationHandlerType] rather than inline parameters. This adapts e-kirjasto's
   * [AudioBookCredentials] onto that interface. Indirect bearer-token fulfillment (basic auth that
   * yields a bearer-token document) continues to be handled automatically by the HTTP client.
   */

  private fun authorizationHandlerFor(
    credentials: AudioBookCredentials?
  ): PlayerAuthorizationHandlerType {
    return object : PlayerAuthorizationHandlerType {
      override fun onAuthorizationIsNoLongerInvalid(
        source: PlayerManifestLink,
        kind: PlayerDownloadRequest.Kind
      ) = Unit

      override fun onAuthorizationIsInvalid(
        source: PlayerManifestLink,
        kind: PlayerDownloadRequest.Kind
      ) = Unit

      override fun onConfigureAuthorizationFor(
        source: PlayerManifestLink,
        kind: PlayerDownloadRequest.Kind
      ): LSHTTPAuthorizationType? {
        return when (credentials) {
          is AudioBookCredentials.UsernamePassword ->
            LSHTTPAuthorizationBasic.ofUsernamePassword(credentials.userName, credentials.password)
          is AudioBookCredentials.UsernameOnly ->
            LSHTTPAuthorizationBasic.ofUsernamePassword(credentials.userName, "")
          is AudioBookCredentials.BearerToken ->
            LSHTTPAuthorizationBearerToken.ofToken(credentials.accessToken)
          null ->
            null
        }
      }

      override fun <T : Any> onRequireCustomCredentialsFor(
        providerName: String,
        kind: PlayerDownloadRequest.Kind,
        credentialsType: Class<T>
      ): T {
        if (credentialsType == OPAUsernamePassword::class.java) {
          when (val c = credentials) {
            is AudioBookCredentials.UsernamePassword ->
              return credentialsType.cast(
                OPAUsernamePassword(c.userName, OPAPassword.Password(c.password))
              )
            is AudioBookCredentials.UsernameOnly ->
              return credentialsType.cast(
                OPAUsernamePassword(c.userName, OPAPassword.NotRequired)
              )
            else ->
              Unit
          }
        }
        throw UnsupportedOperationException("No available credentials of type $credentialsType")
      }
    }
  }

  private fun onManifestFulfillmentEvent(event: ManifestFulfillmentEvent) {
    this.logger.debug("onManifestFulfillmentEvent: {}", event.message)
    this.eventSubject.onNext(event.message)
  }
}
