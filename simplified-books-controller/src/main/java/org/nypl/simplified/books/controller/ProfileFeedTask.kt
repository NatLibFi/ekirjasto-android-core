package org.nypl.simplified.books.controller

import com.io7m.jfunctional.None
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountLoginState
import org.nypl.simplified.books.book_registry.BookRegistryReadableType
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookWithStatus
import org.nypl.simplified.books.formats.api.BookFormatSupportType
import org.nypl.simplified.feeds.api.Feed
import org.nypl.simplified.feeds.api.FeedBooksSelection
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedFacet
import org.nypl.simplified.feeds.api.FeedFacet.FeedFacetPseudo.FilteringForAccount
import org.nypl.simplified.feeds.api.FeedFacet.FeedFacetPseudo.Sorting
import org.nypl.simplified.feeds.api.FeedFacet.FeedFacetPseudo.Sorting.SortBy
import org.nypl.simplified.feeds.api.FeedFacet.FeedFacetPseudo.FilteringForFeed
import org.nypl.simplified.feeds.api.FeedFacet.FeedFacetPseudo.FilteringForFeed.FilterBy
import org.nypl.simplified.feeds.api.FeedSearch
import org.nypl.simplified.profiles.controller.api.ProfileFeedRequest
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.slf4j.LoggerFactory
import java.util.ArrayList
import java.util.Collections
import java.util.Locale
import java.util.concurrent.Callable

internal class ProfileFeedTask(
  private val bookFormatSupport: BookFormatSupportType,
  private val bookRegistry: BookRegistryReadableType,
  private val profiles: ProfilesControllerType,
  private val request: ProfileFeedRequest
) : Callable<Feed.FeedWithoutGroups> {

  private val logger =
    LoggerFactory.getLogger(ProfileFeedTask::class.java)

  override fun call(): Feed.FeedWithoutGroups {
    this.logger.debug("generating local feed")

    /*
     * Generate facets.
     */

    //Chose the tabs we want to show on the my books page
    val doSelectFacets = request.feedSelection != FeedBooksSelection.BOOKS_FEED_SELECTED
    val facetGroups = this.makeFacets(doSelectFacets)
    val facets = facetGroups.values.flatten()

    val feed =
      Feed.empty(
        feedURI = this.request.uri,
        feedID = this.request.id,
        feedSearch = FeedSearch.FeedSearchLocal,
        feedTitle = this.request.title,
        feedFacets = facets,
        feedFacetGroups = facetGroups
      )

    try {
      this.logger.debug("book registry contains {} books", this.bookRegistry.books().size)
      val books = this.collectAllBooks(this.bookRegistry)
      this.logger.debug("collected {} candidate books", books.size)

      // Count how many heldAvailable books are in registry
      // and mark into feed
      feed.booksHeldReady = countHeldReady(books)

      //Get the correct filter based on the bookStatus of the books in the registry
      val filter = this.selectFeedFilter(this.request)
      //Filter the books based on their bookStatus
      this.filterBooks(filter, books)
      this.logger.debug("after filtering, {} candidate books remain", books.size)
      //If we look at selected feed, filter out the non selected books
      //This is not done by the filtering, since the selection can not be seen from bookStatus
      if(this.request.feedSelection == FeedBooksSelection.BOOKS_FEED_SELECTED) {
        this.searchSelectedBooks(books)
        this.logger.debug("after selecting, {} candidate books remain", books.size)
      }
      //If there is a search term, filter the unfitting books out
      this.searchBooks(this.request.search, books)
      this.logger.debug("after searching, {} candidate books remain", books.size)
      //Sort the books in the way we want
      this.sortBooks(this.request.sortBy, books)
      this.logger.debug("after sorting, {} candidate books remain", books.size)

      //Create feed entries for all the books
      for (book in books) {
        feed.entriesInOrder.add(
          FeedEntry.FeedEntryOPDS(
            accountID = book.book.account,
            feedEntry = book.book.entry
          )
        )
      }

      return feed
    } finally {
      this.logger.debug("generated a local feed with {} entries", feed.size)
    }
  }

  /**
   * Create the facets shown on the top of the feed. If fragment should handle
   * multiple different feeds (or selections), create the feed selection facets.
   * @param showSelectionFacets true, if fragment should have navigation facets for different feeds
   */
  private fun makeFacets(showSelectionFacets: Boolean): Map<String, List<FeedFacet>> {
    //The facets for switching between multiple feeds
    val selecting = this.makeSelectionFacets()
    //The facets for sorting
    val sorting = this.makeSortingFacets()
    //The facets for filtering
    val filtering = this.makeFilteringFacets()
    val results = mutableMapOf<String, List<FeedFacet>>()
    //If we want to show the feed facet, add it
    if (showSelectionFacets) {
      results[selecting.first] = selecting.second
    }
    results[sorting.first] = sorting.second
    results[filtering.first] = filtering.second
    return results.toMap()
  }

  private fun makeFilteringFacets(): Pair<String, List<FeedFacet>> {
    val facets = mutableListOf<FeedFacet>()
    val accounts = this.profiles.profileCurrent().accounts().values
    for (account in accounts) {
      val active = account.id == this.request.filterByAccountID
      val title = account.provider.displayName
      facets.add(FilteringForAccount(title, active, account.id))
    }

    facets.add(
      FilteringForAccount(
        title = this.request.facetTitleProvider.collectionAll,
        isActive = this.request.filterByAccountID == null,
        account = null
      )
    )
    return Pair(this.request.facetTitleProvider.collection, facets)
  }

  private fun makeSelectionFacets(): Pair<String, List<FeedFacet>> {
    val facets = mutableListOf<FeedFacet>()
    //Create entries for all FilterBy options
    val values = FilterBy.entries.toTypedArray()
    //Create a facet for each of the bookFilters
    for (filterFacet in values) {
      //Set the activity of the facet based on the FilterBy of the request
      val active = filterFacet == this.request.filterBy
      //Get the translatable title of the tab
      val title =
        when (filterFacet) {
          FilterBy.FILTER_BY_LOANS -> this.request.facetTitleProvider.showTabLoans
          FilterBy.FILTER_BY_HOLDS -> this.request.facetTitleProvider.showTabHolds
          FilterBy.FILTER_BY_SELECTED -> this.request.facetTitleProvider.showTabSelected
        }
      //Determine which feed should be shown, based on  what we're filtering by
      val selectedFeed =
        when(filterFacet) {
          FilterBy.FILTER_BY_LOANS -> FeedBooksSelection.BOOKS_FEED_LOANED
          FilterBy.FILTER_BY_HOLDS -> FeedBooksSelection.BOOKS_FEED_HOLDS
          FilterBy.FILTER_BY_SELECTED -> FeedBooksSelection.BOOKS_FEED_SELECTED
        }
      //Currently we don't want to show the selected feed in the books tab
      //So we don't add it to the facets

      if (selectedFeed != FeedBooksSelection.BOOKS_FEED_SELECTED) {
        facets.add(FilteringForFeed(title, active, selectedFeed, filterFacet))
      }
    }
    //The name is not shown anywhere, so have it just be something relevant
    return Pair("FeedTabs", facets)
  }

  private fun makeSortingFacets(): Pair<String, List<FeedFacet>> {
    val facets = mutableListOf<FeedFacet>()
    val values = SortBy.entries.toTypedArray()
    for (sortingFacet in values) {
      val active = sortingFacet == this.request.sortBy
      val title =
        when (sortingFacet) {
          SortBy.SORT_BY_AUTHOR -> this.request.facetTitleProvider.sortByAuthor
          SortBy.SORT_BY_TITLE -> this.request.facetTitleProvider.sortByTitle
        }
      facets.add(Sorting(title, active, sortingFacet))
    }
    return Pair(this.request.facetTitleProvider.sortBy, facets)
  }

  /**
   * Filter the given books by the given search terms.
   */

  private fun searchBooks(
    search: String?,
    books: ArrayList<BookWithStatus>
  ) {
    if (search == null) {
      return
    }

    val termsUpper = this.searchTermsSplitUpper(search)
    val iterator = books.iterator()
    while (iterator.hasNext()) {
      val book = iterator.next()
      if (!this.searchMatches(termsUpper, book)) {
        iterator.remove()
      }
    }
  }

  /**
   * Removes books that should not be in the selected book list.
   */
  private fun searchSelectedBooks(
    books: ArrayList<BookWithStatus>
  ) {
    val iterator = books.iterator()
    while (iterator.hasNext()) {
      val book = iterator.next()
      //If there is no selected info, remove from books
      if (book.book.entry.selected is None) {
        iterator.remove()
      }
    }
  }

  /**
   * Count how many of the books in the registry have status HeldReady
   */
  private fun countHeldReady(
    books: ArrayList<BookWithStatus>
  ) : Int {
    return books.count { bookHeldReady(it.status) }
  }

  /**
   * Split the given search string into a list of uppercase search terms.
   */

  private fun searchTermsSplitUpper(search: String): List<String> {
    val terms = search.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    val termsUpper = ArrayList<String>(8)
    for (term in terms) {
      termsUpper.add(term.uppercase(Locale.ROOT))
    }
    return termsUpper
  }

  /**
   * Sort the list of books by the given facet.
   */

  private fun sortBooks(
    sortBy: SortBy,
    books: ArrayList<BookWithStatus>
  ) {
    when (sortBy) {
      SortBy.SORT_BY_AUTHOR -> this.sortBooksByAuthor(books)
      SortBy.SORT_BY_TITLE -> this.sortBooksByTitle(books)
    }
  }

  private fun sortBooksByTitle(books: ArrayList<BookWithStatus>) {
    books.sortWith { book0, book1 ->
      val entry0 = book0.book.entry
      val entry1 = book1.book.entry
      entry0.title.compareTo(entry1.title)
    }
  }

  private fun sortBooksByAuthor(books: ArrayList<BookWithStatus>) {
    books.sortWith { book0, book1 ->
      val entry0 = book0.book.entry
      val entry1 = book1.book.entry
      val authors1 = entry0.authors
      val authors2 = entry1.authors
      val e0 = authors1.isEmpty()
      val e1 = authors2.isEmpty()
      if (e0 && e1) {
        0
      } else if (e0) {
        1
      } else if (e1) {
        -1
      } else {
        val author1 = authors1[0]!!
        val author2 = authors2[0]!!
        author1.compareTo(author2)
      }
    }
  }

  /**
   * Filter the list of books with the given filter.
   */

  private fun filterBooks(
    filter: (BookStatus) -> Boolean,
    books: ArrayList<BookWithStatus>
  ) {
    val iter = books.iterator()
    while (iter.hasNext()) {
      val book = iter.next()

      if (!isBookSupported(book)) {
        iter.remove()
        continue
      }
      if (!filter.invoke(book.status)) {
        iter.remove()
        continue
      }
    }
  }

  private fun isBookSupported(book: BookWithStatus): Boolean {
    for (format in book.book.formats) {
      if (this.bookFormatSupport.isDRMSupported(format.drmInformation.kind)) {
        return true
      }
    }
    return false
  }

  private fun collectAllBooks(bookRegistry: BookRegistryReadableType): ArrayList<BookWithStatus> {
    val accountID = this.request.filterByAccountID
    val values = bookRegistry.books().values
    val allBooks =
      if (accountID != null) {
        values.filter { book -> book.book.account == accountID }
      } else {
        values
      }.filter {
        this.accountIsLoggedIn(it.book.account)
      }
    return ArrayList(allBooks)
  }

  private fun accountIsLoggedIn(accountID: AccountID): Boolean {
    return try {
      val account = this.profiles.profileCurrent().account(accountID)
      if (!account.provider.authentication.isLoginPossible) {
        true
      } else {
        account.loginState is AccountLoginState.AccountLoggedIn
      }
    } catch (e: Exception) {
      false
    }
  }

  private fun bookHeldReady(status: BookStatus): Boolean {
    return when (status) {
      is BookStatus.Held.HeldReady -> true
      else -> false
    }
  }

  private fun usableForLoansFeed(status: BookStatus): Boolean {
    return when (status) {
      is BookStatus.Held,
      is BookStatus.Holdable,
      is BookStatus.Loanable,
      is BookStatus.ReachedLoanLimit,
      is BookStatus.Revoked ->
        false
      is BookStatus.Downloading,
      is BookStatus.DownloadWaitingForExternalAuthentication,
      is BookStatus.DownloadExternalAuthenticationInProgress,
      is BookStatus.FailedDownload,
      is BookStatus.FailedLoan,
      is BookStatus.FailedRevoke,
      is BookStatus.Loaned -> true
      is BookStatus.RequestingDownload,
      is BookStatus.RequestingLoan,
      is BookStatus.Selected -> false
      is BookStatus.Unselected -> false
      is BookStatus.RequestingRevoke ->
        true
    }
  }

  private fun usableForHoldsFeed(status: BookStatus): Boolean {
    return when (status) {
      is BookStatus.Held ->
        true
      is BookStatus.Downloading,
      is BookStatus.DownloadWaitingForExternalAuthentication,
      is BookStatus.DownloadExternalAuthenticationInProgress,
      is BookStatus.FailedDownload,
      is BookStatus.FailedLoan,
      is BookStatus.FailedRevoke,
      is BookStatus.Holdable,
      is BookStatus.Loanable,
      is BookStatus.Loaned,
      is BookStatus.ReachedLoanLimit,
      is BookStatus.RequestingDownload,
      is BookStatus.RequestingLoan,
      is BookStatus.RequestingRevoke,
      is BookStatus.Selected -> false
      is BookStatus.Unselected -> false
      is BookStatus.Revoked ->
        false
    }
  }

  /**
   * Return true if the book is usable for selected feed
   */
  private fun usableForSelectedFeed(status: BookStatus): Boolean {
    //Allow the usage for any BookStatus, except a book when the book is just unselected
    return when (status) {
      is BookStatus.Unselected -> false
      else -> true
    }
  }

  /**
   * @return `true` if any of the given search terms match the given book, or the list of
   * search terms is empty
   */

  private fun searchMatches(
    termsUpper: List<String>,
    book: BookWithStatus
  ): Boolean {
    if (termsUpper.isEmpty()) {
      return true
    }

    for (index in termsUpper.indices) {
      val termUpper = termsUpper[index]
      val ee = book.book.entry
      val eTitle = ee.title.uppercase(Locale.ROOT)
      if (eTitle.contains(termUpper)) {
        return true
      }

      val authors = ee.authors
      for (a in authors) {
        if (a.uppercase(Locale.ROOT).contains(termUpper)) {
          return true
        }
      }
    }

    return false
  }

  private fun selectFeedFilter(
    request: ProfileFeedRequest
  ): (BookStatus) -> Boolean {
    return when (request.feedSelection) {
      FeedBooksSelection.BOOKS_FEED_LOANED -> ::usableForLoansFeed
      FeedBooksSelection.BOOKS_FEED_HOLDS -> ::usableForHoldsFeed
      FeedBooksSelection.BOOKS_FEED_SELECTED -> ::usableForSelectedFeed
    }
  }
}
