package org.nypl.simplified.analytics.circulation

import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.addCredentialsToProperties
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.getAccessToken
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.analytics.api.AnalyticsConfiguration
import org.nypl.simplified.analytics.api.AnalyticsEvent
import org.nypl.simplified.analytics.api.AnalyticsSystem
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.ExecutorService

/**
 * An analytics system based on Circulation Analytics.
 */

class CirculationAnalyticsSystem(
  private val configuration: AnalyticsConfiguration,
  private val executor: ExecutorService
) : AnalyticsSystem {

  private val logger =
    LoggerFactory.getLogger(CirculationAnalyticsSystem::class.java)

  override fun onAnalyticsEvent(event: AnalyticsEvent): Unit =
    this.executor.execute { this.consumeEvent(event) }

  private fun consumeEvent(event: AnalyticsEvent) {
    this.logger.debug("received event {}", event::class.simpleName)
    when (event) {
      is AnalyticsEvent.BookOpened -> {
        event.targetURI?.let { target ->
          postURI(event.onAccessTokenUpdated, target, event.credentials)
        }
        this.logger.debug("consuming 'BookOpened' event for {}", event.targetURI)
      }
      else -> {
        // All other events are silently dropped
      }
    }
  }

  private fun postURI(
    onAccessTokenUpdated: (String?) -> Unit,
    target: URI,
    credentials: AccountAuthenticationCredentials?
  ) {
    val request =
      this.configuration.http.newRequest(target)
        .setAuthorization(AccountAuthenticatedHTTP.createAuthorizationIfPresent(credentials))
        .addCredentialsToProperties(credentials)
        .build()

    val response = request.execute()
    when (val status = response.status) {
      is LSHTTPResponseStatus.Responded.OK -> {
        onAccessTokenUpdated(status.getAccessToken())
      }
      is LSHTTPResponseStatus.Responded.Error -> {
        logError(status)
      }
      is LSHTTPResponseStatus.Failed -> {
        this.logger.error("error sending event to {}: ", target, status.exception)
      }
    }
  }

  private fun logError(status: LSHTTPResponseStatus) {
    val problemReport = status.properties?.problemReport
    if (problemReport != null) {
      this.logger.debug("status: {}", problemReport.status)
      this.logger.debug("title:  {}", problemReport.title)
      this.logger.debug("type:   {}", problemReport.type)
      this.logger.debug("detail: {}", problemReport.detail)
    }
  }
}
