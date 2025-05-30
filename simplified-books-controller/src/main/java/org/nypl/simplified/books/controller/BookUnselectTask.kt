package org.nypl.simplified.books.controller

import com.io7m.jfunctional.None
import com.io7m.jfunctional.OptionType
import com.io7m.jfunctional.Some
import one.irradia.mime.api.MIMECompatibility
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPRequestBuilderType
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.librarysimplified.mdc.MDCKeys
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.addCredentialsToProperties
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.BookIDs
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryType
import org.nypl.simplified.books.book_registry.BookRegistry
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookWithStatus
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParser
import org.nypl.simplified.opds.core.OPDSAvailabilityLoanable
import org.nypl.simplified.opds.core.OPDSAvailabilityType
import org.nypl.simplified.opds.core.OPDSParseException
import org.nypl.simplified.opds.core.getOrNull
import org.nypl.simplified.profiles.api.ProfileID
import org.nypl.simplified.profiles.api.ProfilesDatabaseType
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI

/**
 * Task for unselecting a particular book, removing it from the favorites list.
 * Extends the AbstractBookTask class.
 */

class BookUnselectTask (
  private val accountID: AccountID,
  profileID: ProfileID,
  profiles: ProfilesDatabaseType,
  private val HTTPClient: LSHTTPClientType,
  private val feedEntry: OPDSAcquisitionFeedEntry,
  private val bookRegistry: BookRegistryType
) : AbstractBookTask(accountID, profileID, profiles) {

  override val taskRecorder: TaskRecorderType =
    TaskRecorder.create()
  override val logger =
    LoggerFactory.getLogger(BookUnselectTask::class.java)

  override fun execute(account: AccountType): TaskResult.Success<Unit> {
    taskRecorder.beginNewStep("Creating an OPDS Unselect...")

    try {
      logger.debug("setting up book database entry")
      val database = account.bookDatabase
      val bookID = BookIDs.newFromOPDSEntry(feedEntry)
      val databaseEntry = database.entry(bookID)

      MDC.put(MDCKeys.BOOK_INTERNAL_ID, databaseEntry.book.id.value())
      MDC.put(MDCKeys.BOOK_TITLE, databaseEntry.book.entry.title)
      MDCKeys.put(MDCKeys.BOOK_PUBLISHER, databaseEntry.book.entry.publisher)

      //Using the alternate link as the base for request
      val baseURI = feedEntry.alternate.getOrNull()
      if (baseURI == null) {
        logger.debug("No link to form")
        return taskRecorder.finishSuccess(Unit)
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
            //If successful, store returned value to bookRegistry
            this.handleOKRequest(account, currentURI, status, databaseEntry, bookID)
            return taskRecorder.finishSuccess(Unit)
          }
          is LSHTTPResponseStatus.Responded.Error -> {
            this.handleHTTPError(status)
            return taskRecorder.finishSuccess(Unit)
          }
          is LSHTTPResponseStatus.Failed -> {
            this.handleHTTPFailure(status)
            return taskRecorder.finishSuccess(Unit)
          }
        }
      }
    } catch (e: SelectTaskException.SelectAccessTokenExpired) {
      //Catch a special error for access token expiration
      throw e
    }
  }

  override fun onFailure(result: TaskResult.Failure<Unit>) {
    //Do nothing
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

    if (status.properties.status == 401) {
      //Create an exception that is handled in AbstractBookTask and forwarded to Controller,
      //From where the BookUnselectTask was called
      val message = String.format("bookSelect failed, bad credentials")
      val exception = IOException(message)
      //Fail the current step
      this.taskRecorder.currentStepFailed(
        message = message,
        errorCode = "accessTokenExpired",
        exception = exception
      )
      this.logger.debug("refresh credentials due to 401 server response")
      //Failure is checked and handled in Controller, where the tokenRefresh is triggered
      //Don't set as logged out, as can possibly be logged in with tokenRefresh
      throw TaskFailedHandled(exception)

    } else {

      val report = status.properties.problemReport
      if (report != null) {
        taskRecorder.addAttributes(report.toMap())
      }

      taskRecorder.currentStepFailed(
        message = "HTTP request failed: ${status.properties.originalStatus} ${status.properties.message}",
        errorCode = "UnselectError",
        exception = null
      )

      throw SelectTaskException.UnselectTaskFailed()
    }
  }

  /**
   * Update the database and registry so that the selected info is removed
   */
  private fun handleOKRequest(
    account: AccountType,
    uri: URI,
    status: LSHTTPResponseStatus.Responded.OK,
    databaseEntry: BookDatabaseEntryType,
    bookID: BookID
  ) {
    //If the book is on loan, the loan info should be retained, and the selected info removed

    //Get the input stream from the delete
    val inputStream = status.bodyStream ?: ByteArrayInputStream(ByteArray(0))

    //Returned value,without the selected info
    val entry = this.parseOPDSFeedEntry(inputStream, uri)

    //Make a new value from response with old availability info from database
    val newEntry = OPDSAcquisitionFeedEntry.newBuilderFrom(entry)
      .setAvailability(databaseEntry.book.entry.availability)
      .build()

    //Update the database and take the new answer to be put into the registry
    val newValue = account.bookDatabase.createOrUpdate(bookID, newEntry)

    //Old status
    val statusOld = this.bookRegistry.bookStatusOrNull(bookID)

    //Shows the correct popup before possibly removing the entry
    this.bookRegistry.update(
      BookWithStatus(
        newValue.book,
        BookStatus.Unselected(bookID, statusOld))
    )

    taskRecorder.currentStepSucceeded("Book unselected successfully")
  }

  private fun parseOPDSFeedEntry(
    inputStream: InputStream,
    uri: URI
  ): OPDSAcquisitionFeedEntry {
    taskRecorder.beginNewStep("Parsing the OPDS feed entry...")
    val parser = OPDSAcquisitionFeedEntryParser.newParser()

    return try {
      inputStream.use {
        parser.parseEntryStream(uri, it)
      }
    } catch (e: OPDSParseException) {
      logger.error("OPDS feed parse error: ", e)
      taskRecorder.currentStepFailed(
        message = "Failed to parse the OPDS feed entry (${e.message}).",
        errorCode = "Parse error",
        exception = e
      )
      throw SelectTaskException.UnselectTaskFailed()
    }
  }
}
