package org.nypl.simplified.books.controller.api

import com.google.common.util.concurrent.FluentFuture
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.borrowing.SAMLDownloadContext
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.taskrecorder.api.TaskResult

/**
 * The books controller.
 */

interface BooksControllerType {

  /**
   * Attempt to borrow the given book.
   *
   * @param accountID The account that will receive the book
   * @param bookID The book ID
   * @param entry The OPDS feed entry for the book
   */

  fun bookBorrow(
    accountID: AccountID,
    bookID: BookID,
    entry: OPDSAcquisitionFeedEntry,
    samlDownloadContext: SAMLDownloadContext? = null
  ): FluentFuture<TaskResult<*>>

  /**
   * Dismiss a failed book borrowing.
   *
   * @param accountID The account that failed to receive the book
   * @param bookID The ID of the book
   */

  fun bookBorrowFailedDismiss(
    accountID: AccountID,
    bookID: BookID
  )

  /**
   * Cancel a book download.
   *
   * @param accountID The account that would be receiving the book
   * @param bookID The ID of the book
   */

  fun bookCancelDownloadAndDelete(
    accountID: AccountID,
    bookID: BookID
  ): FluentFuture<TaskResult<Unit>>

  /**
   * Submit a problem report for a book
   *
   * @param accountID The account that owns the book
   * @param feedEntry Feed entry, used to get the URI to submit to
   * @param reportType Type of report to submit
   */

  fun bookReport(
    accountID: AccountID,
    feedEntry: FeedEntry.FeedEntryOPDS,
    reportType: String
  ): FluentFuture<TaskResult<Unit>>

  /**
   * Sync all books for the given account.
   *
   * @param accountID The account ID
   */

  fun booksSync(
    accountID: AccountID
  ): FluentFuture<TaskResult<Unit>>

  /**
   * Revoke the given book.
   *
   * @param accountID The account
   * @param bookId The ID of the book
   * @param onNewBookEntry The action to perform after receiving a new feed entry
   */

  fun bookRevoke(
    accountID: AccountID,
    bookId: BookID,
    onNewBookEntry: (FeedEntry.FeedEntryOPDS) -> Unit = { }
  ): FluentFuture<TaskResult<Unit>>

  /**
   * Delete the given book.
   *
   * @param accountID The account
   * @param bookId The ID of the book
   */

  fun bookDelete(
    accountID: AccountID,
    bookId: BookID
  ): FluentFuture<TaskResult<Unit>>

  /**
   * Dismiss a failed book revocation.
   *
   * @param accountID The account that failed to revoke the book
   * @param bookID The ID of the book
   */

  fun bookRevokeFailedDismiss(
    accountID: AccountID,
    bookID: BookID
  ): FluentFuture<TaskResult<Unit>>

  /**
   * Add the chosen book to a list of selected books.
   *
   * @param accountID The account that selected the book
   * @param feedEntry The FeedEntry for the book
   */
  fun bookAddToSelected(
    accountID: AccountID,
    feedEntry: FeedEntry.FeedEntryOPDS
  ) : FluentFuture<TaskResult<*>>

  /**
   * Remove the chosen book to a list of selected books.
   *
   * @param accountID The account that removed the book
   * @param bookID The ID of the book
   */
  fun bookRemoveFromSelected(
    accountID: AccountID,
    feedEntry: FeedEntry.FeedEntryOPDS
  ) : FluentFuture<TaskResult<*>>
}
