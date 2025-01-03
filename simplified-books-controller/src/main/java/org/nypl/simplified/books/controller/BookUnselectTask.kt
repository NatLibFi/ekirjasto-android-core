package org.nypl.simplified.books.controller

import one.irradia.mime.api.MIMECompatibility
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPRequestBuilderType
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.addCredentialsToProperties
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.opds.core.getOrNull
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.slf4j.LoggerFactory
import java.net.URI

class BookUnselectTask (
  private val HTTPClient: LSHTTPClientType,
  private val account: AccountType,
  private val feedEntry: OPDSAcquisitionFeedEntry
) {

  private lateinit var taskRecorder: TaskRecorderType

  private val logger =
    LoggerFactory.getLogger(BookUnselectTask::class.java)

  fun execute() : TaskResult<*> {
    this.taskRecorder = TaskRecorder.create()
    taskRecorder.beginNewStep("Creating an OPDS Unselect...")

    try {
      //Using the alternate link as it seems to be the same basis as the ref "self"
      //FIXFIX
      // See if better solution to get the link
      val baseURI = feedEntry.alternate.getOrNull()
      if (baseURI == null) {
        logger.debug("No link to form")
        return taskRecorder.finishFailure<Unit>()
      }
      //Form the select URI by adding the desired path
      val currentURI = URI.create(baseURI.toString().plus("/unselect_book"))

      taskRecorder.beginNewStep("Using $currentURI to unselect a book...")
      taskRecorder.addAttribute("Unselect URI", currentURI.toString())

      //Use the credentials available on current profile
      val credentials = account.loginState.credentials

      //Create the authorization (bearer) based on current credentials
      val auth =
        AccountAuthenticatedHTTP.createAuthorizationIfPresent(credentials)

      //Use the DELETE method to ask server to remove the book to selected with auth
      val request =
        HTTPClient.newRequest(currentURI!!)
          .setMethod(
            LSHTTPRequestBuilderType.Method.Delete(
              ByteArray(0),
              MIMECompatibility.applicationOctetStream
            )
          )
          .setAuthorization(auth)
          .addCredentialsToProperties(credentials)
          .build()

      request.execute().use { response ->
        when (val status = response.status) {
          is LSHTTPResponseStatus.Responded.OK -> {
            //If successful, answer with success, handle feed refresh elsewhere
            this.handleOKRequest(currentURI, status)
            return taskRecorder.finishSuccess("Success")
          }
          is LSHTTPResponseStatus.Responded.Error -> {
            this.handleHTTPError(status)
            return taskRecorder.finishFailure<Unit>()
          }
          is LSHTTPResponseStatus.Failed -> {
            this.handleHTTPFailure(status)
            return taskRecorder.finishFailure<Unit>()
          }
        }
      }
    } catch (e: SelectTaskException.SelectAccessTokenExpired) {
      //Catch a special error for access token expiration
      throw e
    }
  }

  private fun handleHTTPFailure(
    status: LSHTTPResponseStatus.Failed
  ) {
    taskRecorder.currentStepFailed(
      message = status.exception.message ?: "Exception raised during connection attempt.",
      errorCode = "SelectingFailed",
      exception = status.exception
    )
    throw SelectTaskException.UnselectTaskFailed()
  }

  private fun handleHTTPError(
    status: LSHTTPResponseStatus.Responded.Error
  ) {
    val report = status.properties.problemReport
    if (report != null) {
      taskRecorder.addAttributes(report.toMap())

      //FIXFIX
      //Report type for already unselected book?
      if (report.type == "http://librarysimplified.org/terms/problem/selection-already-removed") {
        taskRecorder.currentStepSucceeded("It turns out we already didn't have this book selected.")
        return
      }
    }

    taskRecorder.currentStepFailed(
      message = "HTTP request failed: ${status.properties.originalStatus} ${status.properties.message}",
      errorCode = "UnselectError",
      exception = null
    )

    //Catch 401 with special handling in controller
    if (report?.type == "http://librarysimplified.org/terms/problem/credentials-invalid") {
      throw SelectTaskException.SelectAccessTokenExpired()
    }

    throw SelectTaskException.UnselectTaskFailed()
  }

  private fun handleOKRequest(
    uri: URI,
    status: LSHTTPResponseStatus.Responded.OK
  ) {
    taskRecorder.currentStepSucceeded("Book selected successfully")
  }
}
