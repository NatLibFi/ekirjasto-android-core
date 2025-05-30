package org.librarysimplified.ui.catalog

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.ui.errorpage.ErrorPageParameters

sealed class CatalogFeedEvent {

  object GoUpwards : CatalogFeedEvent()

  data class OpenErrorPage(
    val parameters: ErrorPageParameters
  ) : CatalogFeedEvent()

  data class LoginRequired(
    val account: AccountID
  ) : CatalogFeedEvent()

  data class OpenFeed(
    val feedArguments: CatalogFeedArguments
  ) : CatalogFeedEvent()

  data class OpenBookDetail(
    val feedArguments: CatalogFeedArguments,
    val opdsEntry: FeedEntry.FeedEntryOPDS
  ) : CatalogFeedEvent()

  data class OpenViewer(
    val book: Book,
    val format: BookFormat
  ) : CatalogFeedEvent()

  /**
   * Removes views not currently visible, so they are recreated with
   * up to date information after login.
   */
  data object RefreshViews :CatalogFeedEvent()
}
