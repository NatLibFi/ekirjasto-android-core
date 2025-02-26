package org.nypl.simplified.books.controller

import com.io7m.jfunctional.Some
import org.joda.time.DateTime
import org.librarysimplified.mdc.MDCKeys
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.book_database.api.BookDatabaseException
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookWithStatus
import org.nypl.simplified.profiles.api.ProfileID
import org.nypl.simplified.profiles.api.ProfilesDatabaseType
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

class BookDeleteTask(
  accountID: AccountID,
  profileID: ProfileID,
  profiles: ProfilesDatabaseType,
  private val bookID: BookID,
  private val bookRegistry: BookRegistryType,
) : AbstractBookTask(accountID, profileID, profiles) {

  override val logger: Logger =
    LoggerFactory.getLogger(BookDeleteTask::class.java)

  override val taskRecorder: TaskRecorderType =
    TaskRecorder.create()

  @Throws(BookDatabaseException::class)
  override fun execute(account: AccountType): TaskResult.Success<Unit> {
    this.logger.debug("[{}] deleting book", this.bookID.brief())
    this.taskRecorder.beginNewStep("Deleting book...")

    MDC.put(MDCKeys.ACCOUNT_INTERNAL_ID, account.id.uuid.toString())
    MDC.put(MDCKeys.ACCOUNT_PROVIDER_NAME, account.provider.displayName)
    MDC.put(MDCKeys.ACCOUNT_PROVIDER_ID, account.provider.id.toString())

    return try {
      val entry = account.bookDatabase.entry(this.bookID)
      MDC.put(MDCKeys.BOOK_INTERNAL_ID, entry.book.id.value())
      MDC.put(MDCKeys.BOOK_TITLE, entry.book.entry.title)
      MDCKeys.put(MDCKeys.BOOK_PUBLISHER, entry.book.entry.publisher)

      //If the book is still selected, don't delete the book, just update
      if (entry.book.entry.selected is Some<DateTime>) {
        logger.debug("Book is selected, don't delete, just update")
        this.bookRegistry.update(BookWithStatus(entry.book, BookStatus.fromBook(entry.book)))
      } else {
        //Otherwise delete the db and registry entries
        logger.debug("Book not selected delete from database and register")
        entry.delete()
        this.bookRegistry.clearFor(entry.book.id)
      }
      this.taskRecorder.finishSuccess(Unit)
    } catch (e: Exception) {
      this.taskRecorder.currentStepFailed(
        message = e.message ?: e.javaClass.canonicalName ?: "unknown",
        errorCode = "deleteFailed",
        exception = e
      )
      throw TaskFailedHandled(e)
    }
  }

  override fun onFailure(result: TaskResult.Failure<Unit>) {
    // Nothing to do
  }
}
