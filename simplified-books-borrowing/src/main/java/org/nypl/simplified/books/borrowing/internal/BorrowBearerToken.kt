package org.nypl.simplified.books.borrowing.internal

import one.irradia.mime.api.MIMECompatibility
import one.irradia.mime.api.MIMEType
import org.librarysimplified.http.bearer_token.LSSimplifiedBearerTokenNegotiation
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountReadableType
import org.nypl.simplified.books.borrowing.BorrowContextType
import org.nypl.simplified.books.borrowing.BorrowSubtaskCredentials
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskFactoryType
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskType
import org.nypl.simplified.books.formats.api.StandardFormatNames
import java.net.URI

/**
 * A task that negotiates a Simplified bearer token. When the token is negotiated, the link within
 * the token, and the actual token value, are pushed into the borrow context to be used by the
 * next subtask.
 *
 * palace.http 1.x handled bearer tokens transparently inside the HTTP client via an
 * auto-registered interceptor. palace.http 2.x removed that interceptor in favour of the explicit
 * [LSSimplifiedBearerTokenNegotiation] API used here.
 */

class BorrowBearerToken : BorrowSubtaskType {

  companion object : BorrowSubtaskFactoryType {
    override val name: String
      get() = "Bearer Token Negotiation"

    override fun createSubtask(): BorrowSubtaskType {
      return BorrowBearerToken()
    }

    override fun isApplicableFor(
      type: MIMEType,
      target: URI?,
      account: AccountReadableType?
    ): Boolean {
      return MIMECompatibility.isCompatibleStrictWithoutAttributes(
        type,
        StandardFormatNames.simplifiedBearerToken
      )
    }
  }

  override fun execute(context: BorrowContextType) {
    context.taskRecorder.beginNewStep("Handling bearer token negotiation...")
    context.bookDownloadIsRunning("Requesting download...", receivedSize = 0L)

    return try {
      val currentURI =
        context.currentURICheck()
      val credentials =
        context.account.loginState.credentials
      val auth =
        AccountAuthenticatedHTTP.createAuthorizationIfPresent(credentials)

      when (
        val result =
          LSSimplifiedBearerTokenNegotiation.negotiate(
            client = context.httpClient,
            target = currentURI,
            refreshTokenProperties = null,
            authorization = auth
          )
      ) {
        is LSSimplifiedBearerTokenNegotiation.NegotiationFailed ->
          this.handleNegotiationFailure(context, currentURI, result)
        is LSSimplifiedBearerTokenNegotiation.NegotiationSucceeded ->
          this.handleNegotiationSuccess(context, currentURI, result)
      }
    } catch (e: BorrowSubtaskException.BorrowSubtaskFailed) {
      context.bookDownloadFailed()
      throw e
    }
  }

  private fun handleNegotiationFailure(
    context: BorrowContextType,
    currentURI: URI,
    result: LSSimplifiedBearerTokenNegotiation.NegotiationFailed
  ) {
    val report = result.problemReport
    if (report != null) {
      context.taskRecorder.addAttributes(report.toMap())
    }
    context.logError("bearer token negotiation failed: {}", result.message)
    context.taskRecorder.currentStepFailed(
      message = "Bearer token negotiation failed for $currentURI: ${result.message}",
      errorCode = BorrowErrorCodes.httpRequestFailed,
      exception = result.exception
    )
    context.bookDownloadFailed()
    throw BorrowSubtaskException.BorrowSubtaskFailed()
  }

  private fun handleNegotiationSuccess(
    context: BorrowContextType,
    currentURI: URI,
    result: LSSimplifiedBearerTokenNegotiation.NegotiationSucceeded
  ) {
    val token = result.token
    context.logDebug("negotiated bearer token; following to {}", token.location)
    context.setNextSubtaskCredentials(
      BorrowSubtaskCredentials.UseBearerToken(
        refreshURI = currentURI,
        token = token.accessToken
      )
    )
    context.receivedNewURI(token.location)
    context.taskRecorder.currentStepSucceeded("Bearer token negotiated.")
  }
}
