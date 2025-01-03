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

class BookSelectTask (
  private val HTTPClient: LSHTTPClientType,
  private val account: AccountType,
  private val feedEntry: OPDSAcquisitionFeedEntry
) {

  private lateinit var taskRecorder: TaskRecorderType

  private val logger =
    LoggerFactory.getLogger(BookSelectTask::class.java)

  fun execute() : TaskResult<*> {
    this.taskRecorder = TaskRecorder.create()
    taskRecorder.beginNewStep("Creating an OPDS Select...")

    try {
      //Using the alternate link as it seems to be the same basis as the ref "self"
      //FIXFIX
      // See if better solution to get the link
      val baseURI = feedEntry.alternate.getOrNull()
      if (baseURI == null) {
        logger.debug("No alternate link to use as basis for request")
        return taskRecorder.finishFailure<Unit>()
      }

      //Form the select URI by adding the desired path
      val currentURI = URI.create(baseURI.toString().plus("/select_book"))

      taskRecorder.beginNewStep("Using $currentURI to select a book...")
      taskRecorder.addAttribute("Select URI", currentURI.toString())

      //Use the credentials available on current profile
      val credentials = account.loginState.credentials

      //Create the authorization (bearer) based on current credentials
      val auth =
        AccountAuthenticatedHTTP.createAuthorizationIfPresent(credentials)

      //Use the POST method to ask server to add the book to selected with selected auth
      val request =
        HTTPClient.newRequest(currentURI!!)
          .setMethod(
            LSHTTPRequestBuilderType.Method.Post(
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
    throw SelectTaskException.SelectTaskFailed()
  }

  private fun handleHTTPError(
    status: LSHTTPResponseStatus.Responded.Error
  ) {
    val report = status.properties.problemReport
    if (report != null) {
      taskRecorder.addAttributes(report.toMap())

      //FIXFIX
      //Report type for already selected book?
      if (report.type == "http://librarysimplified.org/terms/problem/book-already-selected") {
        taskRecorder.currentStepSucceeded("It turns out we already selected this book.")
        return
      }
    }

    taskRecorder.currentStepFailed(
      message = "HTTP request failed: ${status.properties.originalStatus} ${status.properties.message}",
      errorCode = "SelectedError",
      exception = null
    )

    if (report?.type == "http://librarysimplified.org/terms/problem/select-limit-reached") {
      //FIXFIX
      //DO we have special error for list being full? set to 50?
      throw SelectTaskException.SelectTaskFailed()
    }

    //Catch 401 with special handling in controller
    if (report?.type == "http://librarysimplified.org/terms/problem/credentials-invalid") {
      throw SelectTaskException.SelectAccessTokenExpired()
    }

    throw SelectTaskException.SelectTaskFailed()
  }

  private fun handleOKRequest(
    uri: URI,
    status: LSHTTPResponseStatus.Responded.OK
  ) {
    taskRecorder.currentStepSucceeded("Book selected successfully")
  }
}
