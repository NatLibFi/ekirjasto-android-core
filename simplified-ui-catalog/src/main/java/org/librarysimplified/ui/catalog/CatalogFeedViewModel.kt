package org.librarysimplified.ui.catalog

import android.content.res.Resources
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import com.google.common.util.concurrent.FluentFuture
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.PublishSubject
import net.jcip.annotations.GuardedBy
import org.joda.time.DateTime
import org.joda.time.LocalDateTime
import org.librarysimplified.mdc.MDCKeys
import org.librarysimplified.ui.catalog.CatalogFeedArguments.CatalogFeedArgumentsLocalBooks
import org.librarysimplified.ui.catalog.CatalogFeedArguments.CatalogFeedArgumentsAllLocalBooks
import org.librarysimplified.ui.catalog.CatalogFeedArguments.CatalogFeedArgumentsRemote
import org.librarysimplified.ui.catalog.CatalogFeedState.CatalogFeedLoaded
import org.librarysimplified.ui.catalog.CatalogFeedState.CatalogFeedLoaded.CatalogFeedEmpty
import org.librarysimplified.ui.catalog.CatalogFeedState.CatalogFeedLoaded.CatalogFeedWithGroups
import org.librarysimplified.ui.catalog.CatalogFeedState.CatalogFeedLoaded.CatalogFeedWithoutGroups
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.accounts.api.AccountEventCreation
import org.nypl.simplified.accounts.api.AccountEventDeletion
import org.nypl.simplified.accounts.api.AccountEventLoginStateChanged
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountLoginState
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.api.AccountReadableType
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.analytics.api.AnalyticsEvent
import org.nypl.simplified.analytics.api.AnalyticsType
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookStatusEvent
import org.nypl.simplified.books.book_registry.BookWithStatus
import org.nypl.simplified.books.controller.api.BooksControllerType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.feeds.api.Feed
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedFacet
import org.nypl.simplified.feeds.api.FeedFacet.FeedFacetPseudo
import org.nypl.simplified.feeds.api.FeedFacet.FeedFacetPseudo.FilteringForAccount
import org.nypl.simplified.feeds.api.FeedFacet.FeedFacetPseudo.FilteringForFeed
import org.nypl.simplified.feeds.api.FeedFacet.FeedFacetPseudo.Sorting
import org.nypl.simplified.feeds.api.FeedFacetPseudoTitleProviderType
import org.nypl.simplified.feeds.api.FeedLoaderResult
import org.nypl.simplified.feeds.api.FeedLoaderType
import org.nypl.simplified.feeds.api.FeedSearch
import org.nypl.simplified.futures.FluentFutureExtensions.map
import org.nypl.simplified.futures.FluentFutureExtensions.onAnyError
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.opds.core.OPDSAvailabilityHeldReady
import org.nypl.simplified.opds.core.OPDSAvailabilityLoaned
import org.nypl.simplified.opds.core.getOrNull
import org.nypl.simplified.profiles.api.ProfileDateOfBirth
import org.nypl.simplified.profiles.api.ProfileDescription
import org.nypl.simplified.profiles.api.ProfilePreferences
import org.nypl.simplified.profiles.api.ProfileUpdated
import org.nypl.simplified.profiles.controller.api.ProfileFeedRequest
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.nypl.simplified.ui.thread.api.UIExecutor
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.net.URI
import java.util.UUID

/**
 * A view model for the catalog feed fragment.
 */

class CatalogFeedViewModel(
  private val resources: Resources,
  private val profilesController: ProfilesControllerType,
  private val feedLoader: FeedLoaderType,
  private val booksController: BooksControllerType,
  private val bookRegistry: BookRegistryType,
  private val buildConfiguration: BuildConfigurationServiceType,
  private val analytics: AnalyticsType,
  private val borrowViewModel: CatalogBorrowViewModel,
  private val feedArguments: CatalogFeedArguments,
  private val listener: FragmentListenerType<CatalogFeedEvent>
) : ViewModel(), CatalogPagedViewListener {

  private val instanceId =
    UUID.randomUUID()

  private val logger =
    LoggerFactory.getLogger(this.javaClass)

  private val uiExecutor =
    UIExecutor()

  private val stateMutable: MutableLiveData<CatalogFeedState> =
    MutableLiveData(CatalogFeedState.CatalogFeedLoading(this.feedArguments))

  init {
    loadFeed(this.feedArguments)
  }

  private val state: CatalogFeedState
    get() = this.stateLive.value!!

  private class BookModel(
    val feedEntry: FeedEntry.FeedEntryOPDS,
    val onBookChanged: MutableList<(BookWithStatus) -> Unit> = mutableListOf()
  )

  private val bookModels: MutableMap<BookID, BookModel> =
    mutableMapOf()

  private data class LoaderResultWithArguments(
    val arguments: CatalogFeedArguments,
    val result: FeedLoaderResult
  )

  @GuardedBy("loaderResults")
  private val loaderResults =
    PublishSubject.create<LoaderResultWithArguments>()

  private val subscriptions =
    CompositeDisposable(
      this.profilesController.accountEvents()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(this::onAccountEvent),
      this.bookRegistry.bookEvents()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(this::onBookStatusEvent),
      this.loaderResults
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(this::onFeedLoaderResult)
    )

  //Take live data that tells about the state of current account, works as there's only one
  //account (library) so mostRecent is always the correct account
  private val accountLiveMutable: MutableLiveData<AccountType> =
    MutableLiveData(
      this.profilesController
        .profileCurrent()
        .mostRecentAccount()
    )

  val accountLive: LiveData<AccountType> =
    this.accountLiveMutable

  val account: AccountType =
    this.accountLive.value!!

  private fun onAccountEvent(event: AccountEvent) {
    when (event) {
      is AccountEventCreation.AccountEventCreationSucceeded,
      is AccountEventDeletion.AccountEventDeletionSucceeded -> {
        if (this.state.arguments.isLocallyGenerated) {
          this.reloadFeed()
        }
      }
      is AccountEventLoginStateChanged ->
        this.onLoginStateChanged(event.accountID, event.state)
    }
  }

  private fun onLoginStateChanged(accountID: AccountID, accountState: AccountLoginState) {
    val feedState = state

    when (val ownership = feedState.arguments.ownership) {
      is CatalogFeedOwnership.OwnedByAccount -> {
        /*
         * If loading the feed failed due to bad credentials and an account login has occurred,
         * try refreshing the feed.
         */

        if (
          accountState is AccountLoginState.AccountLoggedIn &&
          ownership.accountId == accountID &&
          feedState is CatalogFeedState.CatalogFeedLoadFailed &&
          feedState.failure is FeedLoaderResult.FeedLoaderFailure.FeedLoaderFailedAuthentication
        )  {
          //Happens only on very specific situation when there is a catalog load fail, caused by authentication
          this.logger.debug("reloading feed due to successful login after authentication fail")

          this.reloadFeed()
        }
        if ( accountState is AccountLoginState.AccountNotLoggedIn) {
          this.logger.debug("reloading feed due to log out")
          this.reloadFeed()
        }
        if (accountState is AccountLoginState.AccountLoggedIn) {
          //We reload feed on login since in some login cases,
          //(in cases where there are books stored on the device)
          //the feed shows up empty despite there being loans due to not being updated on login
          //Adding different types of feeds meant that there needs to be a backlog clear
          logger.debug("reloading feed due to successful login")
          this.listener.post(CatalogFeedEvent.RefreshViews)
          this.reloadFeed()
        }
      }
      CatalogFeedOwnership.CollectedFromAccounts -> {
        if (
          accountState is AccountLoginState.AccountLoggedIn ||
          accountState is AccountLoginState.AccountNotLoggedIn
        ) {
          this.logger.debug("reloading feed due to successful login or logout")
          this.reloadFeed()
        }
      }
    }
  }

  private fun onAgeUpdateCompleted(result: ProfileUpdated) {
    when (result) {
      is ProfileUpdated.Succeeded -> {
        when (val ownership = this.state.arguments.ownership) {
          is CatalogFeedOwnership.OwnedByAccount -> {
            val ageChanged =
              result.newDescription.preferences.dateOfBirth != result.oldDescription.preferences.dateOfBirth
            if (ageChanged) {
              val account = this.profilesController.profileCurrent().account(ownership.accountId)
              onAgeUpdateSuccess(account, ownership, result)
            }
          }

          CatalogFeedOwnership.CollectedFromAccounts -> {
            // do nothing
          }
        }
      }
      is ProfileUpdated.Failed -> {
        // do nothing
      }
    }
  }

  private fun onAgeUpdateSuccess(
    account: AccountReadableType,
    ownership: CatalogFeedOwnership.OwnedByAccount,
    result: ProfileUpdated.Succeeded
  ) {
    val now = DateTime.now()
    val oldAge = result.oldDescription.preferences.dateOfBirth?.yearsOld(now)
    val newAge = result.newDescription.preferences.dateOfBirth?.yearsOld(now)
    this.logger.debug("age updated from {} to {}", oldAge, newAge)

    newAge?.let { age ->
      val newParameters = CatalogFeedArgumentsRemote(
        title = this.state.arguments.title,
        ownership = ownership,
        feedURI = account.catalogURIForAge(age),
        isSearchResults = false
      )
      this.loadFeed(newParameters)
    }
  }

  private fun onBookStatusEvent(event: BookStatusEvent) {
    this.bookModels[event.bookId]?.let { model ->
      model.onBookChanged.forEach { callback ->
        this.notifyBookStatus(model.feedEntry, callback)
      }
    }

    if (event is BookStatusEvent.BookStatusEventRemoved && this.state.arguments.isLocallyGenerated) {
      this.reloadFeed()
    } else {
      when (val status = event.statusNow) {
        is BookStatus.Held,
        is BookStatus.Loaned,
        is BookStatus.Revoked -> {
          if (this.state.arguments.isLocallyGenerated) {
            this.reloadFeed()
          }
        }
        is BookStatus.DownloadExternalAuthenticationInProgress,
        is BookStatus.DownloadWaitingForExternalAuthentication,
        is BookStatus.Downloading,
        is BookStatus.FailedDownload,
        is BookStatus.FailedLoan,
        is BookStatus.FailedRevoke,
        is BookStatus.Holdable -> {
          if (this.state.arguments.isLocallyGenerated) {
            //Reload feed so dismissed failed holds are not shown in holds feed
            this.reloadFeed()
          }
        }
        is BookStatus.Loanable -> {
          if (this.state.arguments.isLocallyGenerated) {
            //Reload feed so no failed and dismissed loans show up
            this.reloadFeed()
          }
        }
        is BookStatus.ReachedLoanLimit,
        is BookStatus.RequestingDownload,
        is BookStatus.RequestingLoan,
        is BookStatus.RequestingRevoke,
        is BookStatus.Selected -> {
          //No clue why this needs the status check, but it does, as otherwise it keeps
          //triggering this reload every chance it gets
          if (this.state.arguments.isLocallyGenerated && status is BookStatus.Selected) {
            //Reload the feeds when book selected or unselected so
            //What the user sees in the favorites feed is up to date
            this.reloadFeed()
          }
        }
        is BookStatus.Unselected -> {
          if (this.state.arguments.isLocallyGenerated) {
            //Reload the feeds when book selected or unselected so
            //What the user sees in the favorites feed is up to date
            this.reloadFeed()
          }
        }
        null -> {
          // do nothing
        }
      }
    }
  }

  private fun notifyBookStatus(
    feedEntry: FeedEntry.FeedEntryOPDS,
    callback: (BookWithStatus) -> Unit
  ) {
    val bookWithStatus =
      this.bookRegistry.bookOrNull(feedEntry.bookID)
        ?: this.synthesizeBookWithStatus(feedEntry)

    callback(bookWithStatus)
  }

  private fun synthesizeBookWithStatus(
    item: FeedEntry.FeedEntryOPDS
  ): BookWithStatus {
    val book = Book(
      id = item.bookID,
      account = item.accountID,
      cover = null,
      thumbnail = null,
      entry = item.feedEntry,
      formats = listOf()
    )
    val status = BookStatus.fromBook(book)
    this.logger.debug("Synthesizing {} with status {}", book.id, status)
    return BookWithStatus(book, status)
  }

  override fun onCleared() {
    super.onCleared()
    this.logger.debug("[{}]: deleting viewmodel", this.instanceId)
    this.subscriptions.clear()
    this.uiExecutor.dispose()
  }

  val stateLive: LiveData<CatalogFeedState>
    get() = stateMutable

  fun syncAccounts() {
    when (val arguments = state.arguments) {
      is CatalogFeedArgumentsLocalBooks -> {
        this.syncAccounts(arguments.filterAccount)
      }
      is CatalogFeedArgumentsRemote -> {
      }
      is CatalogFeedArgumentsAllLocalBooks ->
        this.syncAccounts(arguments.filterAccount)
    }
  }

  /**
   * Sync the books in the book register, based on possible accountID.
   */
  private fun syncAccounts(filterAccountID: AccountID?) {
    val profile =
      this.profilesController.profileCurrent()
    val accountsToSync =
      if (filterAccountID == null) {
        // Sync all accounts
        this.logger.debug("[{}]: syncing all accounts", this.instanceId)
        profile.accounts()
      } else {
        // Sync the account we're filtering on
        this.logger.debug("[{}]: syncing account {}", this.instanceId, filterAccountID)
        profile.accounts().filterKeys { it == filterAccountID }
      }

    for (account in accountsToSync.keys) {
      this.booksController.booksSync(account)
    }

    // Feed will be automatically reloaded if necessary in response to BookStatus change.
  }

  fun reloadFeed() {
    this.loadFeed(state.arguments)
  }

  private fun loadFeed(
    arguments: CatalogFeedArguments
  ) {
    return when (arguments) {
      is CatalogFeedArgumentsRemote ->
        this.doLoadRemoteFeed(arguments)
      is CatalogFeedArgumentsLocalBooks ->
        this.doLoadLocalFeed(arguments)
      is CatalogFeedArgumentsAllLocalBooks ->
        this.doLoadLocalCombinationFeed(arguments)
    }
  }
  /**
   * Load a locally-generated feed that has multiple feeds combined.
   */
  private fun doLoadLocalCombinationFeed(
    arguments: CatalogFeedArgumentsAllLocalBooks
  ) {
    this.logger.debug("[{}]: loading local feed {}", this.instanceId, arguments.filterBy.name)

    MDC.remove(MDCKeys.FEED_URI)
    MDC.remove(MDCKeys.ACCOUNT_INTERNAL_ID)
    MDC.remove(MDCKeys.ACCOUNT_PROVIDER_ID)
    MDC.remove(MDCKeys.ACCOUNT_PROVIDER_NAME)

    val booksUri = URI.create("Books")
    val request =
      ProfileFeedRequest(
        facetTitleProvider = CatalogFacetPseudoTitleProvider(this.resources),
        feedSelection = arguments.selection,
        filterByAccountID = arguments.filterAccount,
        filterBy = arguments.filterBy,
        search = arguments.searchTerms,
        sortBy = arguments.sortBy,
        title = arguments.title,
        uri = booksUri
      )

    val future = this.profilesController.profileFeed(request)
      .map { feed ->
        if (arguments.filterBy == FilteringForFeed.FilterBy.FILTER_BY_LOANS) {
          feed.entriesInOrder.removeAll { feedEntry ->
            feedEntry is FeedEntry.FeedEntryOPDS &&
              feedEntry.feedEntry.availability is OPDSAvailabilityLoaned &&
              feedEntry.feedEntry.availability.endDate.getOrNull()?.isBeforeNow == true
          }
        }
        if (arguments.updateHolds) {
          bookRegistry.updateHolds(
            numberOfHolds = feed.entriesInOrder.filter { feedEntry ->
              feedEntry is FeedEntry.FeedEntryOPDS &&
                feedEntry.feedEntry.availability is OPDSAvailabilityHeldReady
            }.size
          )
        }
        FeedLoaderResult.FeedLoaderSuccess(
          feed = feed,
          accessToken = null
        ) as FeedLoaderResult
      }
      .onAnyError { ex -> FeedLoaderResult.wrapException(booksUri, ex) }

    this.createNewStatus(
      account = null,
      arguments = arguments,
      future = future
    )
  }


  /**
   * Load a locally-generated feed.
   */

  private fun doLoadLocalFeed(
    arguments: CatalogFeedArgumentsLocalBooks
  ) {
    this.logger.debug("[{}]: loading local feed {}", this.instanceId, arguments.selection)

    MDC.remove(MDCKeys.FEED_URI)
    MDC.remove(MDCKeys.ACCOUNT_INTERNAL_ID)
    MDC.remove(MDCKeys.ACCOUNT_PROVIDER_ID)
    MDC.remove(MDCKeys.ACCOUNT_PROVIDER_NAME)

    val booksUri = URI.create("Books")

    val request =
      ProfileFeedRequest(
        facetTitleProvider = CatalogFacetPseudoTitleProvider(this.resources),
        feedSelection = arguments.selection,
        filterByAccountID = arguments.filterAccount,
        search = arguments.searchTerms,
        sortBy = arguments.sortBy,
//        filterStatus = arguments.filterStatus,
        title = arguments.title,
        uri = booksUri
      )

    val future = this.profilesController.profileFeed(request)
      .map { feed ->
        feed.entriesInOrder.removeAll { feedEntry ->
          feedEntry is FeedEntry.FeedEntryOPDS &&
            feedEntry.feedEntry.availability is OPDSAvailabilityLoaned &&
            feedEntry.feedEntry.availability.endDate.getOrNull()?.isBeforeNow == true
        }
        if (arguments.updateHolds) {
          bookRegistry.updateHolds(
            numberOfHolds = feed.entriesInOrder.filter { feedEntry ->
              feedEntry is FeedEntry.FeedEntryOPDS &&
                feedEntry.feedEntry.availability is OPDSAvailabilityHeldReady
            }.size
          )
        }
        FeedLoaderResult.FeedLoaderSuccess(
          feed = feed,
          accessToken = null
        ) as FeedLoaderResult
      }
      .onAnyError { ex -> FeedLoaderResult.wrapException(booksUri, ex) }

    this.createNewStatus(
      account = null,
      arguments = arguments,
      future = future
    )
  }

  /**
   * Load a remote feed.
   */

  private fun doLoadRemoteFeed(
    arguments: CatalogFeedArgumentsRemote
  ) {
    this.logger.debug("[{}]: loading remote feed {}", this.instanceId, arguments.feedURI)

    MDC.put(MDCKeys.FEED_URI, arguments.feedURI.toString())

    val profile =
      this.profilesController.profileCurrent()
    val account =
      profile.account(arguments.ownership.accountId)

    MDC.put(MDCKeys.ACCOUNT_INTERNAL_ID, account.id.uuid.toString())
    MDC.put(MDCKeys.ACCOUNT_PROVIDER_ID, account.provider.id.toString())
    MDC.put(MDCKeys.ACCOUNT_PROVIDER_NAME, account.provider.displayName)

    /*
     * If the remote feed has an age gate, and we haven't given an age, then display an
     * age gate!
     */

    if (shouldDisplayAgeGate(account.provider.authentication, profile.preferences())) {
      this.logger.debug("[{}]: showing age gate", this.instanceId)
      val newState = CatalogFeedState.CatalogFeedAgeGate(arguments)
      this.stateMutable.value = newState
      return
    }

    val loginState =
      account.loginState

    val future =
      this.feedLoader.fetchURI(
        accountID = account.id,
        uri = arguments.feedURI,
        credentials = loginState.credentials,
        method = "GET"
      )

    this.createNewStatus(
      account = account,
      arguments = arguments,
      future = future
    )
  }

  private fun shouldDisplayAgeGate(
    authentication: AccountProviderAuthenticationDescription,
    preferences: ProfilePreferences
  ): Boolean {
    val isCoppa = authentication is AccountProviderAuthenticationDescription.COPPAAgeGate
    return isCoppa && buildConfiguration.showAgeGateUi && preferences.dateOfBirth == null
  }

  /**
   * Create a new feed state for the given operation. The feed is assumed to start in a "loading"
   * state.
   */

  private fun createNewStatus(
    account: AccountType?,
    arguments: CatalogFeedArguments,
    future: FluentFuture<FeedLoaderResult>
  ) {
    val newState =
      CatalogFeedState.CatalogFeedLoading(arguments)

    this.stateMutable.value = newState

    /*
     * Register a callback that updates the feed status when the future completes.
     */

    future.map { feedLoaderResult ->
      updateBasicTokenCredentials(feedLoaderResult, account)

      synchronized(loaderResults) {
        val resultWithArguments = LoaderResultWithArguments(arguments, feedLoaderResult)
        this.loaderResults.onNext(resultWithArguments)
      }
    }
  }

  private fun updateBasicTokenCredentials(
    feedLoaderResult: FeedLoaderResult,
    account: AccountType?
  ) {
    if (feedLoaderResult is FeedLoaderResult.FeedLoaderSuccess) {
      account?.updateBasicTokenCredentials(feedLoaderResult.accessToken)
    }
  }

  private fun onFeedLoaderResult(resultWithArguments: LoaderResultWithArguments) {
    this.onFeedStatusUpdated(resultWithArguments.result, resultWithArguments.arguments)
  }

  private fun onFeedStatusUpdated(
    result: FeedLoaderResult,
    arguments: CatalogFeedArguments
  ) {
    this.logger.debug("[{}]: feed status updated: {}", this.instanceId, result.javaClass)

    this.stateMutable.value = this.feedLoaderResultToFeedState(arguments, result)
  }

  private fun feedLoaderResultToFeedState(
    arguments: CatalogFeedArguments,
    result: FeedLoaderResult
  ): CatalogFeedState {
    return when (result) {
      is FeedLoaderResult.FeedLoaderSuccess ->
        when (val feed = result.feed) {
          is Feed.FeedWithoutGroups ->
            this.onReceivedFeedWithoutGroups(arguments, feed)
          is Feed.FeedWithGroups ->
            this.onReceivedFeedWithGroups(arguments, feed)
        }
      is FeedLoaderResult.FeedLoaderFailure ->
        this.onReceivedFeedFailure(arguments, result)
    }
  }

  private fun onReceivedFeedFailure(
    arguments: CatalogFeedArguments,
    result: FeedLoaderResult.FeedLoaderFailure
  ): CatalogFeedState.CatalogFeedLoadFailed {
    /*
     * If the feed can't be loaded due to an authentication failure, then open
     * the account screen (if possible).
     */

    when (result) {
      is FeedLoaderResult.FeedLoaderFailure.FeedLoaderFailedGeneral -> {
        // Display the error.
      }
      is FeedLoaderResult.FeedLoaderFailure.FeedLoaderFailedAuthentication -> {
        when (val ownership = this.state.arguments.ownership) {
          is CatalogFeedOwnership.OwnedByAccount -> {
            val shouldAuthenticate =
              this.profilesController.profileCurrent()
                .account(ownership.accountId)
                .requiresCredentials

            if (shouldAuthenticate) {
              /*
               * Explicitly deferring the opening of the fragment is required due to the
               * tabbed navigation controller eagerly instantiating fragments and causing
               * fragment transaction exceptions. This will go away when we have a replacement
               * for the navigator library.
               */

              this.listener.post(CatalogFeedEvent.LoginRequired(ownership.accountId))
            }
          }
          CatalogFeedOwnership.CollectedFromAccounts -> {
            // Nothing we can do here! We don't know which account owns the feed.
          }
        }
      }
    }

    return CatalogFeedState.CatalogFeedLoadFailed(
      arguments = arguments,
      failure = result
    )
  }

  private fun onReceivedFeedWithGroups(
    arguments: CatalogFeedArguments,
    feed: Feed.FeedWithGroups
  ): CatalogFeedLoaded {
    if (feed.size == 0) {
      return CatalogFeedEmpty(
        arguments = arguments,
        search = feed.feedSearch,
        title = feed.feedTitle
      )
    }

    return CatalogFeedWithGroups(
      arguments = arguments,
      feed = feed
    )
  }

  private fun onReceivedFeedWithoutGroups(
    arguments: CatalogFeedArguments,
    feed: Feed.FeedWithoutGroups
  ): CatalogFeedLoaded {
    if (feed.entriesInOrder.isEmpty()) {
      return CatalogFeedEmpty(
        arguments = arguments,
        search = feed.feedSearch,
        title = feed.feedTitle,
        facetsByGroup = feed.facetsByGroup
      )
    }

    /*
     * Construct a paged list for infinitely scrolling feeds.
     */

    val dataSourceFactory =
      CatalogPagedDataSourceFactory(
        feedLoader = this.feedLoader,
        initialFeed = feed,
        ownership = this.feedArguments.ownership,
        profilesController = this.profilesController
      )

    val pagedListConfig =
      PagedList.Config.Builder()
        .setEnablePlaceholders(true)
        .setPageSize(100)
        .setMaxSize(250)
        .setPrefetchDistance(25)
        .build()

    val pagedList =
      LivePagedListBuilder(dataSourceFactory, pagedListConfig)
        .build()

    return CatalogFeedWithoutGroups(
      arguments = arguments,
      entries = pagedList,
      facetsInOrder = feed.facetsOrder,
      facetsByGroup = feed.facetsByGroup,
      search = feed.feedSearch,
      title = feed.feedTitle
    )
  }

  private class CatalogFacetPseudoTitleProvider(
    val resources: Resources
  ) : FeedFacetPseudoTitleProviderType {
    override val sortByTitle: String
      get() = this.resources.getString(R.string.feedByTitle)
    override val sortByAuthor: String
      get() = this.resources.getString(R.string.feedByAuthor)
    override val collection: String
      get() = this.resources.getString(R.string.feedCollection)
    override val collectionAll: String
      get() = this.resources.getString(R.string.feedCollectionAll)
    override val sortBy: String
      get() = this.resources.getString(R.string.feedSortBy)
    override val show: String
      get() = this.resources.getString(R.string.feedShow)
    override val showAll: String
      get() = this.resources.getString(R.string.feedShowAll)
    override val showOnLoan: String
      get() = this.resources.getString(R.string.feedShowOnLoan)
    override val showTabSelected: String
      get() = this.resources.getString(R.string.feedFacetSelected)

    override val showTabLoans: String
      get() = this.resources.getString(R.string.feedFacetLoans)

    override val showTabHolds: String
      get() = this.resources.getString(R.string.feedFacetHolds)
  }

  val accountProvider: AccountProviderType?
    get() =
      try {
        when (val ownership = this.feedArguments.ownership) {
          is CatalogFeedOwnership.OwnedByAccount ->
            this.profilesController.profileCurrent()
              .account(ownership.accountId)
              .provider
          is CatalogFeedOwnership.CollectedFromAccounts ->
            null
        }
      } catch (e: Exception) {
        null
      }

  fun isAccountCatalogRoot(): Boolean {
    val parameters = this.feedArguments
    if (parameters !is CatalogFeedArgumentsRemote) {
      return true
    }

    val ownership = this.feedArguments.ownership
    if (ownership !is CatalogFeedOwnership.OwnedByAccount) {
      return false
    }

    val account =
      this.profilesController.profileCurrent()
        .account(ownership.accountId)

    return account.feedIsRoot(parameters.feedURI)
  }

  /**
   * Set synthesized birthdate based on if user is over 13
   */

  fun updateBirthYear(over13: Boolean) {
    profilesController.profileUpdate { description ->
      val years = if (over13) 14 else 0
      this.synthesizeDateOfBirthDescription(description, years)
    }.map(this::onAgeUpdateCompleted, this.uiExecutor)
  }

  private fun synthesizeDateOfBirthDescription(
    description: ProfileDescription,
    years: Int
  ): ProfileDescription {
    val newPreferences =
      description.preferences.copy(dateOfBirth = this.synthesizeDateOfBirth(years))
    return description.copy(preferences = newPreferences)
  }

  private fun synthesizeDateOfBirth(years: Int): ProfileDateOfBirth {
    return ProfileDateOfBirth(
      date = DateTime.now().minusYears(years),
      isSynthesized = true
    )
  }

  fun goUpwards() {
    this.listener.post(CatalogFeedEvent.GoUpwards)
  }

  fun showFeedErrorDetails(failure: FeedLoaderResult.FeedLoaderFailure) {
    this.listener.post(
      CatalogFeedEvent.OpenErrorPage(
        this.errorPageParameters(failure)
      )
    )
  }

  private fun errorPageParameters(
    failure: FeedLoaderResult.FeedLoaderFailure
  ): ErrorPageParameters {
    val taskRecorder = TaskRecorder.create()
    taskRecorder.beginNewStep(this.resources.getString(R.string.catalogFeedLoading))
    taskRecorder.addAttributes(failure.attributes)
    taskRecorder.currentStepFailed(failure.message, "feedLoadingFailed", failure.exception)
    val taskFailure = taskRecorder.finishFailure<Unit>()

    return ErrorPageParameters(
      emailAddress = this.buildConfiguration.supportErrorReportEmailAddress,
      body = "",
      subject = this.buildConfiguration.supportErrorReportSubject,
      attributes = taskFailure.attributes.toSortedMap(),
      taskSteps = taskFailure.steps
    )
  }

  fun performSearch(search: FeedSearch, query: String) {
    this.logSearchToAnalytics(query)
    val feedArguments = this.resolveSearch(search, query)
    this.listener.post(
      CatalogFeedEvent.OpenFeed(feedArguments)
    )
  }

  private fun logSearchToAnalytics(query: String) {
    try {
      val profile = this.profilesController.profileCurrent()
      val accountId =
        when (val ownership = this.feedArguments.ownership) {
          is CatalogFeedOwnership.OwnedByAccount -> ownership.accountId
          is CatalogFeedOwnership.CollectedFromAccounts -> null
        }

      if (accountId != null) {
        val account = profile.account(accountId)
        this.analytics.publishEvent(
          AnalyticsEvent.CatalogSearched(
            timestamp = LocalDateTime.now(),
            credentials = account.loginState.credentials,
            profileUUID = profile.id.uuid,
            accountProvider = account.provider.id,
            accountUUID = account.id.uuid,
            searchQuery = query
          )
        )
      }
    } catch (e: Exception) {
      this.logger.error("could not log to analytics: ", e)
    }
  }

  /**
   * Execute the given search based on the current feed.
   */

  private fun resolveSearch(
    search: FeedSearch,
    query: String
  ): CatalogFeedArguments {
    MDC.put(MDCKeys.FEED_SEARCH_QUERY, query)

    return when (val currentArguments = this.feedArguments) {
      is CatalogFeedArgumentsRemote -> {
        when (search) {
          FeedSearch.FeedSearchLocal -> {
            CatalogFeedArgumentsRemote(
              feedURI = currentArguments.feedURI,
              isSearchResults = true,
              ownership = currentArguments.ownership,
              title = currentArguments.title
            )
          }
          is FeedSearch.FeedSearchOpen1_1 -> {
            CatalogFeedArgumentsRemote(
              feedURI = search.search.getQueryURIForTerms(query),
              isSearchResults = true,
              ownership = currentArguments.ownership,
              title = currentArguments.title
            )
          }
        }
      }

      is CatalogFeedArgumentsLocalBooks -> {
        when (search) {
          FeedSearch.FeedSearchLocal -> {
            CatalogFeedArgumentsLocalBooks(
              filterAccount = currentArguments.filterAccount,
              ownership = currentArguments.ownership,
              searchTerms = query,
              selection = currentArguments.selection,
              sortBy = currentArguments.sortBy,
              title = currentArguments.title,
              updateHolds = currentArguments.updateHolds
            )
          }
          is FeedSearch.FeedSearchOpen1_1 -> {
            CatalogFeedArgumentsLocalBooks(
              filterAccount = currentArguments.filterAccount,
              ownership = currentArguments.ownership,
              searchTerms = query,
              selection = currentArguments.selection,
              sortBy = currentArguments.sortBy,
              title = currentArguments.title,
              updateHolds = currentArguments.updateHolds
            )
          }
        }
      }

      is CatalogFeedArgumentsAllLocalBooks -> {
        when (search) {
          is FeedSearch.FeedSearchLocal -> {
            //Get the tab we are in from the stored values
            //Otherwise search and filter always reset to loans
            val oldValues = state.arguments as CatalogFeedArgumentsAllLocalBooks

            return CatalogFeedArgumentsAllLocalBooks(
              filterAccount = currentArguments.filterAccount,
              ownership = currentArguments.ownership,
              searchTerms = query,
              selection = oldValues.selection,
              sortBy = currentArguments.sortBy,
              filterBy = oldValues.filterBy,
              title = currentArguments.title,
              updateHolds = currentArguments.updateHolds
            )
          }
          is FeedSearch.FeedSearchOpen1_1 -> {
            //Get the tab we are in from the stored values
            //Otherwise search and filter always reset to loans
            val oldValues = state.arguments as CatalogFeedArgumentsAllLocalBooks

            return CatalogFeedArgumentsAllLocalBooks(
              filterAccount = currentArguments.filterAccount,
              ownership = currentArguments.ownership,
              searchTerms = query,
              selection = oldValues.selection,
              sortBy = currentArguments.sortBy,
              filterBy = oldValues.filterBy,
              title = currentArguments.title,
              updateHolds = currentArguments.updateHolds
            )
          }
        }

      }
    }
  }

  fun openFeed(title: String, uri: URI) {
    val feedArguments = this.resolveFeed(title, uri, false)
    this.listener.post(
      CatalogFeedEvent.OpenFeed(feedArguments)
    )
  }

  /**
   * Resolve a given URI as a remote feed. The URI, if non-absolute, is resolved against
   * the current feed arguments in order to produce new arguments to load another feed.
   *
   * @param title The title of the target feed
   * @param uri The URI of the target feed
   * @param isSearchResults `true` if the target feed refers to search results
   */

  private fun resolveFeed(
    title: String,
    uri: URI,
    isSearchResults: Boolean
  ): CatalogFeedArguments {
    return when (val arguments = this.feedArguments) {
      is CatalogFeedArgumentsRemote ->
        CatalogFeedArgumentsRemote(
          feedURI = arguments.feedURI.resolve(uri).normalize(),
          isSearchResults = isSearchResults,
          ownership = arguments.ownership,
          title = title
        )

      is CatalogFeedArgumentsLocalBooks -> {
        throw IllegalStateException(
          "Can't transition local to remote feed: ${this.feedArguments.title} -> $title"
        )
      }

      is CatalogFeedArgumentsAllLocalBooks ->
        throw IllegalStateException(
          "Can't transition local to remote feed: ${this.feedArguments.title} -> $title"
        )
    }
  }

  fun openFacet(facet: FeedFacet) {
    val feedArguments = this.resolveFacet(facet)
    val newState = CatalogFeedState.CatalogFeedLoading(feedArguments)
    this.stateMutable.value = newState
    reloadFeed()
  }

  /**
   * Resolve a given facet as a set of feed arguments.
   *
   * @param facet The facet
   */

  private fun resolveFacet(
    facet: FeedFacet
  ): CatalogFeedArguments {
    return when (val currentArguments = this.feedArguments) {
      is CatalogFeedArgumentsRemote ->
        when (facet) {
          is FeedFacet.FeedFacetOPDS ->
            CatalogFeedArgumentsRemote(
              feedURI = currentArguments.feedURI.resolve(facet.opdsFacet.uri).normalize(),
              isSearchResults = currentArguments.isSearchResults,
              ownership = currentArguments.ownership,
              title = facet.title
            )

          is FeedFacetPseudo ->
            currentArguments
        }

      is CatalogFeedArgumentsLocalBooks -> {
        when (facet) {
          is FeedFacet.FeedFacetOPDS ->
            throw IllegalStateException("Cannot transition from a local feed to a remote feed.")

          is Sorting ->
            CatalogFeedArgumentsLocalBooks(
              filterAccount = currentArguments.filterAccount,
              ownership = currentArguments.ownership,
              searchTerms = currentArguments.searchTerms,
              selection = currentArguments.selection,
              sortBy = facet.sortBy,
              title = facet.title,
              updateHolds = currentArguments.updateHolds
            )

          is FilteringForAccount ->
            CatalogFeedArgumentsLocalBooks(
              filterAccount = facet.account,
              ownership = currentArguments.ownership,
              searchTerms = currentArguments.searchTerms,
              selection = currentArguments.selection,
              sortBy = currentArguments.sortBy,
              title = facet.title,
              updateHolds = currentArguments.updateHolds
            )

          is FilteringForFeed ->
            CatalogFeedArgumentsAllLocalBooks(
              title = facet.title,
              ownership = currentArguments.ownership,
              sortBy = currentArguments.sortBy,
              searchTerms = currentArguments.searchTerms,
              selection = facet.selectedFeed,
              filterBy = facet.filterBy,
              filterAccount = currentArguments.filterAccount,
              updateHolds = currentArguments.updateHolds
            )
        }
      }

      is CatalogFeedArgumentsAllLocalBooks -> {
        when (facet) {
          is FeedFacet.FeedFacetOPDS ->
            throw IllegalStateException("Cannot transition from a local feed to a remote feed.")
          is FilteringForAccount -> {
            //Get the tab we are in from the stored values
            //Otherwise search and filter always reset to loans
            val oldValues = state.arguments as CatalogFeedArgumentsAllLocalBooks

            return CatalogFeedArgumentsAllLocalBooks(
              title = currentArguments.title,
              ownership = currentArguments.ownership,
              sortBy = currentArguments.sortBy,
              searchTerms = currentArguments.searchTerms,
              selection = oldValues.selection,
              filterBy = oldValues.filterBy,
              filterAccount = facet.account,
              updateHolds = currentArguments.updateHolds
            )
          }
          is FilteringForFeed -> {
            // From the old state, use the information of which selection and filter we are currently in
            //Otherwise they will reset to the assumed value of loans
            val oldValues = state.arguments as CatalogFeedArgumentsAllLocalBooks

            return CatalogFeedArgumentsAllLocalBooks(
              title = facet.title,
              ownership = currentArguments.ownership,
              sortBy = oldValues.sortBy,
              searchTerms = currentArguments.searchTerms,
              selection = facet.selectedFeed,
              filterBy = facet.filterBy,
              filterAccount = currentArguments.filterAccount,
              updateHolds = currentArguments.updateHolds
            )
          }
          is Sorting -> {
            // From the old state, use the information of which selection and filter we are currently in
            //Otherwise they will reset to the assumed value of loans
            val oldValues = state.arguments as CatalogFeedArgumentsAllLocalBooks
            return CatalogFeedArgumentsAllLocalBooks(
              title = facet.title,
              ownership = currentArguments.ownership,
              sortBy = facet.sortBy,
              searchTerms = currentArguments.searchTerms,
              selection = oldValues.selection,
              filterBy = oldValues.filterBy,
              filterAccount = currentArguments.filterAccount,
              updateHolds = currentArguments.updateHolds
            )
          }
        }
      }
    }
  }

  override fun openBookDetail(opdsEntry: FeedEntry.FeedEntryOPDS) {
    this.listener.post(
      CatalogFeedEvent.OpenBookDetail(this.feedArguments, opdsEntry)
    )
  }

  override fun selectBook(feedEntry: FeedEntry.FeedEntryOPDS) {
    //Check if logged in
    val account = this.profilesController.profileCurrent().mostRecentAccount()
    if (account.loginState is AccountLoginState.AccountNotLoggedIn) {
      //Show login page if not
      openLoginDialog(account.id)
    } else {
      //Otherwise try selecting the book
      booksController.bookAddToSelected(
        accountID = profilesController.profileCurrent().mostRecentAccount().id,
        feedEntry = feedEntry
      )
    }
  }

  override fun unselectBook(feedEntry: FeedEntry.FeedEntryOPDS) {
    //Check if we are logged in, if not, show login
    val account = this.profilesController.profileCurrent().mostRecentAccount()
    if (account.loginState is AccountLoginState.AccountNotLoggedIn) {
      openLoginDialog(account.id)
    } else {
      //Attempt to unselect
      booksController.bookRemoveFromSelected(
        accountID = profilesController.profileCurrent().mostRecentAccount().id,
        feedEntry = feedEntry
      )
    }
  }

  override fun openBookPreview(feedEntry: FeedEntry.FeedEntryOPDS) {
    // do nothing
  }

  override fun openViewer(book: Book, format: BookFormat) {
    this.listener.post(CatalogFeedEvent.OpenViewer(book, format))
  }

  override fun showTaskError(book: Book, result: TaskResult.Failure<*>) {
    this.logger.debug("showing error: {}", book.id)

    val errorPageParameters = ErrorPageParameters(
      emailAddress = this.buildConfiguration.supportErrorReportEmailAddress,
      body = "",
      subject = this.buildConfiguration.supportErrorReportSubject,
      attributes = result.attributes.toSortedMap(),
      taskSteps = result.steps
    )
    this.listener.post(CatalogFeedEvent.OpenErrorPage(errorPageParameters))
  }

  override fun resetInitialBookStatus(feedEntry: FeedEntry.FeedEntryOPDS) {
    val initialBookStatus = synthesizeBookWithStatus(feedEntry)

    this.bookModels[feedEntry.bookID]?.let { model ->
      model.onBookChanged.forEach { callback ->
        callback(initialBookStatus)
      }
    }

    this.bookRegistry.update(initialBookStatus)
  }

  /**
   * Reset the book in registry to its previous status, or generate a new one from the available information
   * if there isn't one
   */
  override fun resetPreviousBookStatus(bookID: BookID, status: BookStatus, selected: Boolean) {
    logger.debug("Resetting status: {}", status)
    //Cast tho the correct status depending if select is true or not
    val previousStatus: BookStatus? = if (selected) {
      val currentStatus = status as BookStatus.Selected
      currentStatus.previousStatus
    } else {
      val currentStatus = status as BookStatus.Unselected
      currentStatus.previousStatus
    }
    // If there is a previous status, set that as the new status in book registry
    if (previousStatus != null) {
      val bookWithStatus = this.bookRegistry.bookOrNull(bookID)
      this.bookRegistry.update(BookWithStatus(bookWithStatus!!.book, previousStatus))
    } else {
      // The book for certain has been added to the bookRegistry so if no status provided, create
      //one from the bookRegistry entry
      val bookWithStatus = this.bookRegistry.bookOrNull(bookID)
      this.bookRegistry.update(BookWithStatus(bookWithStatus!!.book, BookStatus.fromBook(bookWithStatus.book)))
    }
  }

  override fun registerObserver(
    feedEntry: FeedEntry.FeedEntryOPDS,
    callback: (BookWithStatus) -> Unit
  ) {
    this.bookModels.getOrPut(feedEntry.bookID, { BookModel(feedEntry) }).onBookChanged.add(callback)
    this.notifyBookStatus(feedEntry, callback)
  }

  override fun unregisterObserver(
    feedEntry: FeedEntry.FeedEntryOPDS,
    callback: (BookWithStatus) -> Unit
  ) {
    val model = this.bookModels[feedEntry.bookID]
    if (model != null) {
      model.onBookChanged.remove(callback)
      if (model.onBookChanged.isEmpty()) {
        this.bookModels.remove(feedEntry.bookID)
      }
    }
  }

  override fun dismissBorrowError(feedEntry: FeedEntry.FeedEntryOPDS) {
    this.borrowViewModel.tryDismissBorrowError(feedEntry.accountID, feedEntry.bookID)
  }

  override fun dismissRevokeError(feedEntry: FeedEntry.FeedEntryOPDS) {
    this.borrowViewModel.tryDismissRevokeError(feedEntry.accountID, feedEntry.bookID)
  }

  override fun delete(feedEntry: FeedEntry.FeedEntryOPDS) {
    this.borrowViewModel.tryDelete(feedEntry.accountID, feedEntry.bookID)
  }

  /**
   * Try to cancel the current download of a book
   */
  override fun cancelDownload(feedEntry: FeedEntry.FeedEntryOPDS) {
    this.borrowViewModel.tryCancelDownload(feedEntry.accountID, feedEntry.bookID)
    //Since canceling download deletes the book from local database, we need to sync with
    //the circulation so that the loan is shown correctly
    this.syncAccounts()
  }

  override fun borrowMaybeAuthenticated(book: Book) {
    this.openLoginDialogIfNecessary(book.account)
    this.borrowViewModel.tryBorrowMaybeAuthenticated(book)
  }

  override fun reserveMaybeAuthenticated(book: Book) {
    this.openLoginDialogIfNecessary(book.account)
    this.borrowViewModel.tryReserveMaybeAuthenticated(book)
  }

  override fun revokeMaybeAuthenticated(book: Book) {
    this.openLoginDialogIfNecessary(book.account)
    this.borrowViewModel.tryRevokeMaybeAuthenticated(book)
  }

  fun title(): String {
    return this.feedArguments.title
  }

  private fun openLoginDialogIfNecessary(accountID: AccountID) {
    if (this.borrowViewModel.isLoginRequired(accountID)) {
      this.listener.post(
        CatalogFeedEvent.LoginRequired(accountID)
      )
    }
  }
  /**
   * Opens the login dialog without any checks
   */
  override fun openLoginDialog(accountID: AccountID) {
    this.listener.post(
      CatalogFeedEvent.LoginRequired(accountID)
    )
  }
}
