package org.nypl.simplified.books.controller

import org.librarysimplified.mdc.MDCKeys
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryType
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookStatus.Loaned.LoanedNotDownloaded
import org.nypl.simplified.books.book_registry.BookWithStatus
import org.nypl.simplified.profiles.api.ProfileID
import org.nypl.simplified.profiles.api.ProfilesDatabaseType
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

class BookFileDeleteTask (
  accountID: AccountID,
  profileID: ProfileID,
  profiles: ProfilesDatabaseType,
  private val bookID: BookID,
  private val bookRegistry: BookRegistryType,
  ) : AbstractBookTask(accountID, profileID, profiles) {

  private lateinit var databaseEntry: BookDatabaseEntryType

  override val logger: Logger =
    LoggerFactory.getLogger(BookRevokeTask::class.java)

  override val taskRecorder: TaskRecorderType =
    TaskRecorder.create()

  @Throws(Exception::class)
  override fun execute(account: AccountType): TaskResult.Success<Unit> {
    MDC.put(MDCKeys.ACCOUNT_INTERNAL_ID, account.id.uuid.toString())
    MDC.put(MDCKeys.ACCOUNT_PROVIDER_NAME, account.provider.displayName)
    MDC.put(MDCKeys.ACCOUNT_PROVIDER_ID, account.provider.id.toString())

    this.logger.debug("[{}] files removal", this.bookID.brief())
    this.taskRecorder.beginNewStep("Removing files...")

    //Get the database entry for the book
    this.logger.debug("setting up book database entry")
    val database = account.bookDatabase
    this.databaseEntry = database.entry(this.bookID)

    //Get the book and status of the book
    val book = database.entry(this.bookID).book
    val status = BookStatus.fromBook(book) as BookStatus.Loaned.LoanedDownloaded

    //Create a new status, where the book is not downloaded
    val newStatus = LoanedNotDownloaded(
      id = status.id,
      loanExpiryDate = status.loanExpiryDate,
      returnable = status.returnable,
      isOpenAccess = false
    )

    //Delete the book data
    this.databaseEntry.deleteBookData()

    //Update the book registry, with the status of not downloaded
    this.bookRegistry.update(BookWithStatus(book, newStatus))

    //
    return this.taskRecorder.finishSuccess(Unit)
    }

    override fun onFailure(result: TaskResult.Failure<Unit>) {
      // Nothing to do
    }
  }
