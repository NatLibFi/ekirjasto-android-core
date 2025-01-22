package org.nypl.simplified.books.controller

import one.irradia.mime.api.MIMECompatibility
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPRequestBuilderType
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.addCredentialsToProperties
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookIDs
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookWithStatus
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParser
import org.nypl.simplified.opds.core.OPDSParseException
import org.nypl.simplified.opds.core.getOrNull
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URI

class BookSelectTask (
  private val HTTPClient: LSHTTPClientType,
  private val account: AccountType,
  private val feedEntry: OPDSAcquisitionFeedEntry,
  private val bookRegistry: BookRegistryType
) {

  private lateinit var taskRecorder: TaskRecorderType

  private val logger =
    LoggerFactory.getLogger(BookSelectTask::class.java)

  fun execute() : TaskResult<*> {
    this.taskRecorder = TaskRecorder.create()

    //First create database entry, so we have something we can post status to

    this.taskRecorder.beginNewStep("Create database entry")
    //ID of the book, can be new
    val bookID = BookIDs.newFromOPDSEntry(this.feedEntry)

    val bookInitial =
      Book(
        id = bookID,
        account = account.id,
        cover = null,
        thumbnail = null,
        entry = feedEntry,
        formats = listOf()
      )

    // Get either the book that already was in database, or initialize a new database entry
    val book = this.getOrCreateBookDatabaseEntry(bookInitial, feedEntry)

    this.taskRecorder.currentStepSucceeded("Initial database entry successful")

    //Try to add the selection into the database entry
    taskRecorder.beginNewStep("Creating an OPDS Select...")
    try {
      //Using the alternate link as the basis for the request
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
            // Parse the answer and store the new value to the database and book register
            this.handleOKRequest(currentURI, status, book)
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

  private fun getOrCreateBookDatabaseEntry(
    book: Book,
    entry: OPDSAcquisitionFeedEntry
  ): Book {
    this.taskRecorder.beginNewStep("Setting up a book database entry...")

    try {
      val database = this.account.bookDatabase

      //If the book is already in the database, use that book instead of the generated one
      //If the book is not, we initialize a db entry with the given book
      if (!database.books().contains(book.id)) {
        database.createOrUpdate(book.id, entry)
      }
      //Get the entry
      val dbEntry = database.entry(book.id)
      this.taskRecorder.currentStepSucceeded("Book database initialized for select")
      return dbEntry.book
    } catch (e: Exception) {
      logger.error("[{}]: failed to set up book database: ", book.id.brief(), e)
      this.taskRecorder.currentStepFailed(
        message = "Could not set up the book database entry.",
        errorCode = "Error",
        exception = e
      )
      throw SelectTaskException.SelectTaskFailed()
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
    }

    taskRecorder.currentStepFailed(
      message = "HTTP request failed: ${status.properties.originalStatus} ${status.properties.message}",
      errorCode = "SelectedError",
      exception = null
    )

    //Catch 401 with special handling in controller
    if (report?.type == "http://librarysimplified.org/terms/problem/credentials-invalid") {
      throw SelectTaskException.SelectAccessTokenExpired()
    }

    throw SelectTaskException.SelectTaskFailed()
  }

  private fun handleOKRequest(
    uri: URI,
    status: LSHTTPResponseStatus.Responded.OK,
    book: Book
  ) {
    //Get the server response
    val inputStream = status.bodyStream ?: ByteArrayInputStream(ByteArray(0))

    // new entry created from ONLY response
    val entry = this.parseOPDSFeedEntry(inputStream, uri)

    //get database
    val database = this.account.bookDatabase

    //The version in database might be carrying loan information
    //And just overwriting the existing entry with the new one would not carry that information over
    if (database.books().contains(book.id)) {
      //If book already in db, get the entry and insert the availability information already available
      val dbEntry = database.entry(book.id)
      //Build a new feed entry
      val adjustedEntry = OPDSAcquisitionFeedEntry.newBuilderFrom(entry)
        .setAvailability(dbEntry.book.entry.availability)
        .build()
      //Create the database entry
      logger.debug("Book was in database with availability:")
      logger.debug(dbEntry.book.entry.availability.toString())
      database.createOrUpdate(book.id, adjustedEntry)
    } else {
      //otherwise just add the response entry
      database.createOrUpdate(book.id, entry)
    }

    val updatedEntry = database.entry(book.id)

    //If book has previous status, we want to add it to the status update so it can be reset
    val oldBookStatus = this.bookRegistry.bookStatusOrNull(book.id)
    logger.debug("OLD BOOK STATUS :{}",oldBookStatus)
    //If successful, update the state of the book in database to selected, so other things happen
    this.bookRegistry.updateIfStatusIsMoreImportant(
      BookWithStatus(
        book = updatedEntry.book,
        status = BookStatus.Selected(updatedEntry.book.id, oldBookStatus)
      )
    )

    taskRecorder.currentStepSucceeded("Book selected successfully")
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
      throw SelectTaskException.SelectTaskFailed()
    }
  }
}
