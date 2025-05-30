package org.nypl.simplified.books.controller

import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.AsyncFunction
import com.google.common.util.concurrent.FluentFuture
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.io7m.jfunctional.Some
import com.io7m.junreachable.UnreachableCodeException
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import org.joda.time.Instant
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.services.api.ServiceDirectoryType
import org.nypl.drm.core.AdobeAdeptExecutorType
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.accounts.api.AccountEventUpdated
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountLoginState
import org.nypl.simplified.accounts.api.AccountLoginStringResourcesType
import org.nypl.simplified.accounts.api.AccountLogoutStringResourcesType
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.accounts.database.api.AccountsDatabaseNonexistentException
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryEvent
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.analytics.api.AnalyticsType
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.BookIDs
import org.nypl.simplified.books.book_registry.BookPreviewRegistryType
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookWithStatus
import org.nypl.simplified.books.borrowing.BorrowRequest
import org.nypl.simplified.books.borrowing.BorrowRequirements
import org.nypl.simplified.books.borrowing.BorrowTask
import org.nypl.simplified.books.borrowing.BorrowTaskType
import org.nypl.simplified.books.borrowing.SAMLDownloadContext
import org.nypl.simplified.books.borrowing.internal.BorrowErrorCodes
import org.nypl.simplified.books.controller.api.BookRevokeStringResourcesType
import org.nypl.simplified.books.controller.api.BooksControllerType
import org.nypl.simplified.books.controller.api.BooksPreviewControllerType
import org.nypl.simplified.books.formats.api.BookFormatSupportType
import org.nypl.simplified.books.preview.BookPreviewRequirements
import org.nypl.simplified.books.preview.BookPreviewTask
import org.nypl.simplified.crashlytics.api.CrashlyticsServiceType
import org.nypl.simplified.deeplinks.controller.api.DeepLinkEvent
import org.nypl.simplified.deeplinks.controller.api.DeepLinksControllerType
import org.nypl.simplified.deeplinks.controller.api.ScreenID
import org.nypl.simplified.feeds.api.Feed
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedLoaderType
import org.nypl.simplified.futures.FluentFutureExtensions
import org.nypl.simplified.futures.FluentFutureExtensions.flatMap
import org.nypl.simplified.futures.FluentFutureExtensions.map
import org.nypl.simplified.metrics.api.MetricServiceType
import org.nypl.simplified.notifications.NotificationTokenHTTPCallsType
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.opds.core.OPDSFeedParserType
import org.nypl.simplified.patron.api.PatronUserProfileParsersType
import org.nypl.simplified.profiles.api.ProfileCreationEvent
import org.nypl.simplified.profiles.api.ProfileDeletionEvent
import org.nypl.simplified.profiles.api.ProfileDescription
import org.nypl.simplified.profiles.api.ProfileEvent
import org.nypl.simplified.profiles.api.ProfileID
import org.nypl.simplified.profiles.api.ProfileNoneCurrentException
import org.nypl.simplified.profiles.api.ProfileNonexistentAccountProviderException
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.nypl.simplified.profiles.api.ProfileSelection
import org.nypl.simplified.profiles.api.ProfileType
import org.nypl.simplified.profiles.api.ProfileUpdated
import org.nypl.simplified.profiles.api.ProfilesDatabaseType
import org.nypl.simplified.profiles.api.ProfilesDatabaseType.AnonymousProfileEnabled
import org.nypl.simplified.profiles.api.ProfilesDatabaseType.AnonymousProfileEnabled.ANONYMOUS_PROFILE_ENABLED
import org.nypl.simplified.profiles.controller.api.ProfileAccountCreationStringResourcesType
import org.nypl.simplified.profiles.controller.api.ProfileAccountDeletionStringResourcesType
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest
import org.nypl.simplified.profiles.controller.api.ProfileFeedRequest
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.util.SortedMap
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

/**
 * The default controller implementation.
 */

class Controller private constructor(
  private val cacheDirectory: File,
  private val accountEvents: Subject<AccountEvent>,
  private val profileEvents: Subject<ProfileEvent>,
  private val services: ServiceDirectoryType,
  private val taskExecutor: ListeningExecutorService
) : BooksControllerType,
  BooksPreviewControllerType,
  ProfilesControllerType,
  DeepLinksControllerType {

  private val deepLinkEventsObservable: BehaviorSubject<DeepLinkEvent> =
    BehaviorSubject.create()

  private val borrows: ConcurrentHashMap<BookID, BorrowTaskType>

  private val borrowRequirements: BorrowRequirements
  private val accountLoginStringResources =
    this.services.requireService(AccountLoginStringResourcesType::class.java)
  private val accountLogoutStringResources =
    this.services.requireService(AccountLogoutStringResourcesType::class.java)
  private val accountProviders =
    this.services.requireService(AccountProviderRegistryType::class.java)
  private val adobeDrm =
    this.services.optionalService(AdobeAdeptExecutorType::class.java)
  private val analytics =
    this.services.requireService(AnalyticsType::class.java)
  private val bookRegistry =
    this.services.requireService(BookRegistryType::class.java)
  private val bookPreviewRegistry =
    this.services.requireService(BookPreviewRegistryType::class.java)
  private val bookFormatSupport =
    this.services.requireService(BookFormatSupportType::class.java)
  private val feedLoader =
    this.services.requireService(FeedLoaderType::class.java)
  private val feedParser =
    this.services.requireService(OPDSFeedParserType::class.java)
  private val lsHttp =
    this.services.requireService(LSHTTPClientType::class.java)
  private val notificationTokenHttpCalls =
    this.services.requireService(NotificationTokenHTTPCallsType::class.java)
  private val patronUserProfileParsers =
    this.services.requireService(PatronUserProfileParsersType::class.java)
  private val profileAccountCreationStringResources =
    this.services.requireService(ProfileAccountCreationStringResourcesType::class.java)
  private val profileAccountDeletionStringResources =
    this.services.requireService(ProfileAccountDeletionStringResourcesType::class.java)
  private val profiles =
    this.services.requireService(ProfilesDatabaseType::class.java)
  private val revokeStrings =
    this.services.requireService(BookRevokeStringResourcesType::class.java)
  private val crashlytics =
    this.services.optionalService(CrashlyticsServiceType::class.java)
  private val metrics =
    this.services.optionalService(MetricServiceType::class.java)

  private val temporaryDirectory =
    File(this.cacheDirectory, "tmp")

  private val accountRegistrySubscription: Disposable
  private val accountSubscription: Disposable
  private val profileSelectionSubscription: Disposable
  private val profileUpdateSubscription: Disposable

  private val logger =
    LoggerFactory.getLogger(Controller::class.java)

  init {
    this.borrowRequirements =
      BorrowRequirements.create(
        services = this.services,
        clock = { Instant.now() },
        cacheDirectory = this.cacheDirectory,
        temporaryDirectory = this.temporaryDirectory
      )

    this.borrows =
      ConcurrentHashMap()

    this.accountRegistrySubscription =
      this.accountProviders.events.subscribe(this::onAccountRegistryEvent)

    this.accountSubscription =
      this.accountEvents.ofType(AccountEventUpdated::class.java)
        .subscribe(this::onAccountUpdated)

    this.profileUpdateSubscription =
      this.profileEvents.ofType(ProfileUpdated::class.java)
        .subscribe(this::onProfileUpdated)

    this.profileSelectionSubscription =
      this.profileEvents.ofType(ProfileSelection.ProfileSelectionCompleted::class.java)
        .subscribe(this::onProfileSelectionCompleted)

    /*
     * If the anonymous profile is enabled, then ensure that it is "selected" and will
     * therefore very shortly have all of its books loaded.
     */

    if (this.profiles.anonymousProfileEnabled() == ANONYMOUS_PROFILE_ENABLED) {
      this.logger.debug("initializing anonymous profile")
      this.profileSelect(this.profileCurrent().id)
    }
  }

  private fun onProfileUpdated(event: ProfileUpdated) {
    this.updateCrashlytics()
  }

  private fun onAccountUpdated(event: AccountEventUpdated) {
    this.updateCrashlytics()
  }

  private fun onProfileSelectionCompleted(
    event: ProfileSelection.ProfileSelectionCompleted
  ) {
    if (!this.profileAnyIsCurrent()) {
      return
    }

    /*
     * Attempt to sync books if a profile is selected.
     */

    try {
      this.logger.debug("triggering syncing of all accounts in profile")
      this.profiles.currentProfileUnsafe()
        .accounts()
        .keys
        .forEach { this.booksSync(it) }
    } catch (e: Exception) {
      this.logger.error("failed to trigger book syncing: ", e)
    }

    this.updateCrashlytics()
  }

  private fun updateCrashlytics() {
    //TODO enable crashlytics
//    try {
//      val profile = this.profileCurrent()
//      val crash = this.crashlytics
//      if (crash != null) {
//        ControllerCrashlytics.configureCrashlytics(
//          profile = profile,
//          crashlytics = crash
//        )
//      }
//    } catch (e: ProfileNoneCurrentException) {
//      // No profile is current!
//      return
//    }
  }

  /**
   * Respond to account registry events.
   */

  private fun onAccountRegistryEvent(event: AccountProviderRegistryEvent) {
    if (!this.profileAnyIsCurrent()) {
      return
    }

    return when (event) {
      is AccountProviderRegistryEvent.Updated ->
        this.onAccountRegistryProviderUpdatedEvent(event)
      is AccountProviderRegistryEvent.SourceFailed,
      AccountProviderRegistryEvent.StatusChanged -> {
      }
    }
  }

  private fun onAccountRegistryProviderUpdatedEvent(event: AccountProviderRegistryEvent.Updated) {
    val profileCurrentOpt = this.profiles.currentProfile()
    if (profileCurrentOpt is Some<ProfileType>) {
      val profileCurrent = profileCurrentOpt.get()
      this.submitTask {
        ProfileAccountProviderUpdatedTask(
          profile = profileCurrent,
          accountProviderID = event.id,
          accountProviders = this.accountProviders
        )
      }
    } else {
      this.logger.debug("no profile is current")
    }
  }

  private fun <A> submitTask(task: () -> A): FluentFuture<A> {
    val future = SettableFuture.create<A>()
    this.taskExecutor.execute {
      try {
        future.set(task.invoke())
      } catch (e: Throwable) {
        this.logger.error("exception raised during task execution: ", e)
        future.setException(e)
        throw e
      }
    }
    return FluentFuture.from(future)
  }

  private fun <A> submitTask(task: Callable<A>): FluentFuture<A> {
    val future = SettableFuture.create<A>()
    this.taskExecutor.execute {
      try {
        future.set(task.call())
      } catch (e: Throwable) {
        this.logger.error("exception raised during task execution: ", e)
        future.setException(e)
        throw e
      }
    }
    return FluentFuture.from(future)
  }

  override fun deepLinkEvents(): Observable<DeepLinkEvent> {
    return this.deepLinkEventsObservable
  }

  override fun publishDeepLinkEvent(accountID: AccountID, screenID: ScreenID, barcode: String?) {
    this.deepLinkEventsObservable.onNext(
      DeepLinkEvent.DeepLinkIntercepted(
        accountID = accountID,
        screenID = screenID,
        barcode = barcode
      )
    )
  }

  override fun profiles(): SortedMap<ProfileID, ProfileReadableType> {
    return this.castMap(this.profiles.profiles())
  }

  override fun profileAnonymousEnabled(): AnonymousProfileEnabled {
    return this.profiles.anonymousProfileEnabled()
  }

  @Throws(ProfileNoneCurrentException::class)
  override fun profileCurrent(): ProfileReadableType {
    return this.profiles.currentProfileUnsafe()
  }

  override fun profileEvents(): Observable<ProfileEvent> {
    return this.profileEvents
  }

  override fun profileDelete(
    profileID: ProfileID
  ): FluentFuture<ProfileDeletionEvent> {
    return this.submitTask(
      ProfileDeletionTask(
        this.profiles,
        this.profileEvents,
        profileID
      )
    )
  }

  override fun profileCreate(
    displayName: String,
    accountProvider: AccountProviderType,
    descriptionUpdate: (ProfileDescription) -> ProfileDescription
  ): FluentFuture<ProfileCreationEvent> {
    return this.submitTask(
      ProfileCreationTask(
        displayName = displayName,
        profiles = this.profiles,
        profileEvents = this.profileEvents,
        accountProvider = accountProvider,
        descriptionUpdate = descriptionUpdate
      )
    )
  }

  override fun profileSelect(
    profileID: ProfileID
  ): FluentFuture<Unit> {
    return this.submitTask(
      ProfileSelectionTask(
        analytics = this.analytics,
        bookRegistry = this.bookRegistry,
        events = this.profileEvents,
        id = profileID,
        profiles = this.profiles
      )
    )
  }

  override fun profileAccountLogin(
    request: ProfileAccountLoginRequest
  ): FluentFuture<TaskResult<Unit>> {
    return this.submitTask { this.runProfileAccountLogin(request) }
      .flatMap { result -> this.runSyncIfLoginSucceeded(result, request.accountId) }
  }

  private fun runProfileAccountLogin(
    request: ProfileAccountLoginRequest
  ): TaskResult<Unit> {
    val profile = this.profileCurrent()
    val account = profile.account(request.accountId)
    return ProfileAccountLoginTask(
      adeptExecutor = this.adobeDrm,
      http = this.lsHttp,
      profile = profile,
      account = account,
      notificationTokenHttpCalls = notificationTokenHttpCalls,
      loginStrings = this.accountLoginStringResources,
      patronParsers = this.patronUserProfileParsers,
      request = request
    ).call()
  }

  /**
   * The function that should be called from other parths of the code
   * if they received the 401 request to refresh the accessToken.
   * Function attempts to refresh the token and returns a FluentFuture
   * of the result. On success, the action that triggered the 401 should be
   * attempted again. On failure, user should be asked to log in again.
   */
  override fun profileAccountAccessTokenRefresh(
    request: ProfileAccountLoginRequest.EkirjastoAccessTokenRefresh
  ): FluentFuture<TaskResult<Unit>> {
    //Can be used to show a popup after failure
    //Success just returns task success
    logger.debug("profileAccountAccessTokenRefresh")
    return this.submitTask { this.runProfileAccountAccessTokenRefresh(request) }
      .flatMap { result -> this.runLoginStateChangeIfUpdateFailed(result, request.accountId) }
  }

  /**
   * Function triggered by profileAccountAccessTokenRefresh. Runs the task of refreshing
   * the token and handles login state changes. Requires a ProfileAccountLogin request
   * matching to the EkirjastoAccessTokenRefresh request.
   * Returns the actual task result.
   */
  private fun runProfileAccountAccessTokenRefresh(
    request: ProfileAccountLoginRequest.EkirjastoAccessTokenRefresh
  ): TaskResult<Unit> {
    val profile = this.profileCurrent()
    val account = profile.account(request.accountId)
    //Set the login state to reflect that we are doing some authentication things
    //Is needed because otherwise the token refresh is considered and un-needed login try
    //And is not done
    account.setLoginState(
      AccountLoginState.AccountLoggingInWaitingForExternalAuthentication(
        account.provider.authentication,
        "accessToken refreshing"
      )
    )
    //Run the profileAccountLoginTask, where the request is a
    //ProfileAccountLoginRequest.EkirjastoAccessTokenRefresh
    return ProfileAccountLoginTask(
      adeptExecutor = this.adobeDrm,
      http = this.lsHttp,
      profile = profile,
      account = account,
      notificationTokenHttpCalls = notificationTokenHttpCalls,
      loginStrings = this.accountLoginStringResources,
      patronParsers = this.patronUserProfileParsers,
      request = request
    ).call()
  }

  /**
   * Handle the refresh result in the appropriate manner.
   */
  private fun runLoginStateChangeIfUpdateFailed(
    result: TaskResult<Unit>,
    accountID: AccountID
  ): FluentFuture<TaskResult<Unit>> {
    return when (result) {
      is TaskResult.Success -> {
        this.logger.debug("accessToken update succeeded: retrying previous action")
        //No need to do anything here anymore, so we just return the success value
        FluentFutureExtensions.fluentFutureOfValue(result)
      }
      is TaskResult.Failure -> {
        this.logger.debug("refresh didn't succeed: inform user about logging in again and refresh views")
        //Do not trigger actual logout here, as the books are deleted on logout, which is not user friendly
        //When the logout is not deliberately done by the user
        val profile = this.profileCurrent()
        val account = profile.account(accountID)

        //Instead of full logout, set the state of current account to AccountNotLoggedIn
        //This way the user maintains their downloads
        account.setLoginState(
          AccountLoginState.AccountNotLoggedIn
        )
        FluentFutureExtensions.fluentFutureOfValue(result)
      }
    }
  }

  private fun runSyncIfLoginSucceeded(
    result: TaskResult<Unit>,
    accountID: AccountID
  ): FluentFuture<TaskResult<Unit>> {
    return when (result) {
      is TaskResult.Success -> {
        this.logger.debug("logging in succeeded: syncing account")
        this.booksSync(accountID).map { result }
      }
      is TaskResult.Failure -> {
        this.logger.debug("logging in didn't succeed: not syncing account")
        FluentFutureExtensions.fluentFutureOfValue(result)
      }
    }
  }

  override fun profileAccountCreateOrReturnExisting(
    provider: URI
  ): FluentFuture<TaskResult<AccountType>> {
    return this.submitTask(
      ProfileAccountCreateOrReturnExistingTask(
        accountEvents = this.accountEvents,
        accountProviderID = provider,
        accountProviders = this.accountProviders,
        profiles = this.profiles,
        strings = this.profileAccountCreationStringResources,
        metrics = this.metrics
      )
    )
  }

  override fun profileAccountCreateCustomOPDS(
    opdsFeed: URI
  ): FluentFuture<TaskResult<AccountType>> {
    return this.submitTask(
      ProfileAccountCreateCustomOPDSTask(
        accountEvents = this.accountEvents,
        accountProviderRegistry = this.accountProviders,
        httpClient = this.lsHttp,
        opdsURI = opdsFeed,
        opdsFeedParser = this.feedParser,
        profiles = this.profiles,
        strings = this.profileAccountCreationStringResources,
        metrics = this.metrics
      )
    )
  }

  override fun profileAccountCreate(
    provider: URI
  ): FluentFuture<TaskResult<AccountType>> {
    return this.submitTask(
      ProfileAccountCreateTask(
        accountEvents = this.accountEvents,
        accountProviderID = provider,
        accountProviders = this.accountProviders,
        profiles = this.profiles,
        strings = this.profileAccountCreationStringResources,
        metrics = this.metrics
      )
    )
  }

  override fun profileAccountDeleteByProvider(
    provider: URI
  ): FluentFuture<TaskResult<Unit>> {
    return this.submitTask(
      ProfileAccountDeleteTask(
        accountEvents = this.accountEvents,
        accountProviderID = provider,
        profiles = this.profiles,
        profileEvents = this.profileEvents,
        strings = this.profileAccountDeletionStringResources,
        metrics = this.metrics
      )
    )
  }

  @Throws(ProfileNoneCurrentException::class, AccountsDatabaseNonexistentException::class)
  override fun profileAccountFindByProvider(provider: URI): AccountType {
    val profile = this.profileCurrent()
    return profile.accountsByProvider()[provider]
      ?: throw AccountsDatabaseNonexistentException("No account with provider: $provider")
  }

  override fun accountEvents(): Observable<AccountEvent> {
    return this.accountEvents
  }

  @Throws(ProfileNoneCurrentException::class, ProfileNonexistentAccountProviderException::class)
  override fun profileCurrentlyUsedAccountProviders(): ImmutableList<AccountProviderType> {
    return ImmutableList.sortedCopyOf(
      this.profileCurrent()
        .accountsByProvider()
        .values
        .map { account -> account.provider }
    )
  }

  override fun profileAccountLogout(
    accountID: AccountID
  ): FluentFuture<TaskResult<Unit>> {
    return this.submitTask {
      val profile = this.profileCurrent()
      val account = profile.account(accountID)
      ProfileAccountLogoutTask(
        adeptExecutor = this.adobeDrm,
        account = account,
        bookRegistry = this.bookRegistry,
        feedLoader = this.feedLoader,
        patronParsers = this.patronUserProfileParsers,
        http = this.lsHttp,
        notificationTokenHttpCalls = this.notificationTokenHttpCalls,
        logoutStrings = this.accountLogoutStringResources,
        profile = profile
      ).call()
    }
  }

  override fun profileUpdate(
    update: (ProfileDescription) -> ProfileDescription
  ): FluentFuture<ProfileUpdated> {
    return this.submitTask(
      ProfileUpdateTask(
        this.profileEvents,
        requestedProfileId = null,
        profiles = this.profiles,
        update = update
      )
    )
  }

  override fun profileUpdateFor(
    profile: ProfileID,
    update: (ProfileDescription) -> ProfileDescription
  ): FluentFuture<ProfileUpdated> {
    return this.submitTask(
      ProfileUpdateTask(
        this.profileEvents,
        requestedProfileId = profile,
        profiles = this.profiles,
        update = update
      )
    )
  }

  @Throws(ProfileNoneCurrentException::class)
  override fun profileFeed(
    request: ProfileFeedRequest
  ): FluentFuture<Feed.FeedWithoutGroups> {
    return this.submitTask(
      ProfileFeedTask(
        bookFormatSupport = this.bookFormatSupport,
        bookRegistry = this.bookRegistry,
        profiles = this,
        request = request
      )
    )
  }

  @Throws(ProfileNoneCurrentException::class, AccountsDatabaseNonexistentException::class)
  override fun profileAccountForBook(
    bookID: BookID
  ): AccountType {
    val bookWithStatus = this.bookRegistry.bookOrNull(bookID)
    if (bookWithStatus != null) {
      return this.profileCurrent().account(bookWithStatus.book.account)
    }
    throw UnreachableCodeException()
  }

  override fun bookBorrow(
    accountID: AccountID,
    bookID: BookID,
    entry: OPDSAcquisitionFeedEntry,
    samlDownloadContext: SAMLDownloadContext?
  ): FluentFuture<TaskResult<*>> {
    return this.submitTask(
      Callable<TaskResult<*>> {
        val request =
          BorrowRequest.Start(
            accountId = accountID,
            profileId = this.profileCurrent().id,
            opdsAcquisitionFeedEntry = entry,
            samlDownloadContext = samlDownloadContext
          )

        val borrowTask = BorrowTask.createBorrowTask(this.borrowRequirements, request)
        borrows[bookID] = borrowTask
        borrowTask.execute()
      }
    ).transformAsync(AsyncFunction { taskResult ->
      //Transform the answer if the server responded with 401, marked by the result being
      //"accessToken refresh needed"
      if (taskResult is TaskResult.Success<*> && taskResult.result == "accessToken refresh needed") {
        //Run the accessToken refresh
        executeProfileAccountAccessTokenRefresh(accountID).transformAsync(AsyncFunction { tokenResult ->
          //In order to make the user not have to do anything
          //Try to take the loan again if the accessToken refresh is successful
          if (tokenResult is TaskResult.Success) {
            logger.debug("Attempt to execute borrow again")
            val borrowTask = borrows[bookID]!!
            submitTask(Callable { borrowTask.execute() })
          } else {
            //If token lookup fails, return the need to show the login popup
            Futures.immediateFuture(tokenResult)
          }
        }, MoreExecutors.directExecutor())
      } else {
        //In all other cases, return the value of the original borrowTask
        Futures.immediateFuture(taskResult)
      }
    }, MoreExecutors.directExecutor())
  }

  /**
   * Execute the account token refresh. If it succeeds, the previous task should be rerun.
   */
  fun executeProfileAccountAccessTokenRefresh ( accountID: AccountID
  ): FluentFuture<TaskResult<Unit>>{
    logger.debug("AttemptingAccessTokenRefresh")
    //Lookup all the values needed to make the ProfileAccountLoginRequest
    //We only have ekirjastoProfile, so currentProfile is always the correct one
    val profile = this.profileCurrent()
    val account = profile.account(accountID)
    val credentials = account.loginState.credentials as AccountAuthenticationCredentials.Ekirjasto
    val authenticationDescription = account.provider.authentication as AccountProviderAuthenticationDescription.Ekirjasto
    val accessToken = credentials.accessToken
    //Run the refresh and return the fluent future
    return this.profileAccountAccessTokenRefresh(
      ProfileAccountLoginRequest.EkirjastoAccessTokenRefresh(
        accountId = accountID,
        description = authenticationDescription,
        accessToken = accessToken
      )
    )
  }

  override fun bookBorrowFailedDismiss(
    accountID: AccountID,
    bookID: BookID
  ) {
    this.submitTask(
      BookBorrowFailedDismissTask(
        accountID = accountID,
        profileID = this.profileCurrent().id,
        profiles = this.profiles,
        bookID = bookID,
        bookRegistry = this.bookRegistry,
      )
    )
  }

  override fun bookCancelDownloadAndDelete(
    accountID: AccountID,
    bookID: BookID
  ): FluentFuture<TaskResult<Unit>> {
    this.borrows[bookID]?.cancel()
    return this.bookDelete(accountID, bookID)
  }

  override fun bookReport(
    accountID: AccountID,
    feedEntry: FeedEntry.FeedEntryOPDS,
    reportType: String
  ): FluentFuture<TaskResult<Unit>> {
    throw NotImplementedError()
  }

  override fun booksSync(
    accountID: AccountID
  ): FluentFuture<TaskResult<Unit>> {
    return this.submitTask(
      Callable<TaskResult<Unit>> {
        val syncTask = BookSyncTask(
          accountID = accountID,
          profileID = this.profileCurrent().id,
          profiles = this.profiles,
          accountRegistry = this.accountProviders,
          bookRegistry = this.bookRegistry,
          booksController = this,
          feedParser = this.feedParser,
          feedLoader = this.feedLoader,
          patronParsers = this.patronUserProfileParsers,
          http = this.lsHttp
        )
        syncTask.call()
      }
    ).transformAsync(AsyncFunction { taskResult ->
      //Check if the result was a need to refresh the accessToken
      //BookSyncTask does special error handling, and if the result is 401, it throws
      //A special error, which can be caught with the error code of "accessTokenExpired"
      if (taskResult is TaskResult.Failure<Unit>) {
        logger.debug("SYNC ERROR : {}", taskResult.lastErrorCode)
      }
      if (taskResult is TaskResult.Failure<Unit> && taskResult.lastErrorCode == "accessTokenExpired" ) {
        //In order to make the user not have to do anything
        //Try to sync again if the accessToken refresh is successful
        executeProfileAccountAccessTokenRefresh(accountID).transformAsync(AsyncFunction { tokenResult ->
          if (tokenResult is TaskResult.Success) {
            logger.debug("Attempt to execute bookSync again")
            val syncTask = BookSyncTask(
              accountID = accountID,
              profileID = this.profileCurrent().id,
              profiles = this.profiles,
              accountRegistry = this.accountProviders,
              bookRegistry = this.bookRegistry,
              booksController = this,
              feedParser = this.feedParser,
              feedLoader = this.feedLoader,
              patronParsers = this.patronUserProfileParsers,
              http = this.lsHttp
            )
            submitTask(Callable { syncTask.call() })
          } else {
            //If accessToken refresh fails, return the result that should popup the login
            Futures.immediateFuture(tokenResult)
          }
        }, MoreExecutors.directExecutor())
      } else {
        //In other errors return the normal bookSync answer
        Futures.immediateFuture(taskResult)
      }
    }, MoreExecutors.directExecutor())
  }

  override fun bookRevoke(
    accountID: AccountID,
    bookId: BookID,
    onNewBookEntry: (FeedEntry.FeedEntryOPDS) -> Unit
  ): FluentFuture<TaskResult<Unit>> {
    this.publishRequestingDelete(bookId)
    return this.submitTask(
      Callable<TaskResult<Unit>> {
        val revokeTask = BookRevokeTask(
          accountID = accountID,
          profileID = this.profileCurrent().id,
          profiles = this.profiles,
          adobeDRM = this.adobeDrm,
          bookID = bookId,
          bookRegistry = this.bookRegistry,
          feedLoader = this.feedLoader,
          onNewBookEntry = onNewBookEntry,
          revokeStrings = this.revokeStrings
        )
        revokeTask.call()
      }
    ).transformAsync(AsyncFunction { taskResult ->
      //Check if the result was a need to refresh the accessToken
      //This can be caught with the error code of "accessTokenExpired"
      if (taskResult is TaskResult.Failure<Unit> && taskResult.lastErrorCode == "accessTokenExpired" ) {
        //Try to run revoke again if the accessToken refresh is successful
        executeProfileAccountAccessTokenRefresh(accountID).transformAsync(AsyncFunction { tokenResult ->
          if (tokenResult is TaskResult.Success) {
            logger.debug("Attempt to execute return again")
            val revokeTask = BookRevokeTask(
              accountID = accountID,
              profileID = this.profileCurrent().id,
              profiles = this.profiles,
              adobeDRM = this.adobeDrm,
              bookID = bookId,
              bookRegistry = this.bookRegistry,
              feedLoader = this.feedLoader,
              onNewBookEntry = onNewBookEntry,
              revokeStrings = this.revokeStrings
            )
            submitTask(Callable { revokeTask.call() })
          } else {
            //If accessToken refresh fails, return the result that should popup the login
            Futures.immediateFuture(tokenResult)
          }
        }, MoreExecutors.directExecutor())
      } else {
        //In other errors return the normal bookRevoke answer
        Futures.immediateFuture(taskResult)
      }
    }, MoreExecutors.directExecutor())
  }

  override fun bookDelete(
    accountID: AccountID,
    bookId: BookID
  ): FluentFuture<TaskResult<Unit>> {
    this.publishRequestingDelete(bookId)
    return this.submitTask(
      BookDeleteTask(
        accountID = accountID,
        profileID = this.profileCurrent().id,
        profiles = this.profiles,
        bookID = bookId,
        bookRegistry = this.bookRegistry,
      )
    )
  }

  private fun publishRequestingDelete(bookId: BookID) {
    this.bookRegistry.bookOrNull(bookId)?.let { bookWithStatus ->
      this.bookRegistry.update(
        BookWithStatus(
          book = bookWithStatus.book,
          status = BookStatus.RequestingRevoke(bookId)
        )
      )
    }
  }

  override fun bookRevokeFailedDismiss(
    accountID: AccountID,
    bookID: BookID
  ): FluentFuture<TaskResult<Unit>> {
    return this.submitTask(
      BookRevokeFailedDismissTask(
        accountID = accountID,
        profileID = this.profileCurrent().id,
        profiles = this.profiles,
        bookID = bookID,
        bookRegistry = this.bookRegistry,
      )
    )
  }

  override fun handleBookPreviewStatus(
    entry: FeedEntry.FeedEntryOPDS
  ): FluentFuture<TaskResult<*>> {
    val requirements = BookPreviewRequirements(
      clock = { Instant.now() },
      httpClient = this.lsHttp,
      temporaryDirectory = temporaryDirectory
    )

    return this.submitTask(
      Callable<TaskResult<*>> {
        val bookPreviewTask = BookPreviewTask(
          bookPreviewRegistry = bookPreviewRegistry,
          bookPreviewRequirements = requirements,
          feedEntry = entry.feedEntry,
          format = entry.probableFormat
        )
        bookPreviewTask.execute()
      }
    )
  }

  override fun bookAddToSelected(
    accountID: AccountID,
    feedEntry: FeedEntry.FeedEntryOPDS

  ) : FluentFuture<TaskResult<*>> {
    val profile = this.profileCurrent()

    return this.submitTask(
      Callable<TaskResult<Unit>> {
        val bookSelectTask = BookSelectTask(
          accountID= accountID,
          profileID = profile.id,
          profiles = profiles,
          HTTPClient = this.lsHttp,
          feedEntry = feedEntry.feedEntry,
          bookRegistry = bookRegistry
        )
        bookSelectTask.call()
      }
    ).transformAsync(AsyncFunction { taskResult ->
      //Check if the result was a need to refresh the accessToken
      //Can be caught with the error code of "accessTokenExpired"
      if (taskResult is TaskResult.Failure<Unit> && taskResult.lastErrorCode == "accessTokenExpired" ) {
        //In order to make the user not have to do anything
        //Try to return select the book again if the accessToken refresh is successful
        executeProfileAccountAccessTokenRefresh(accountID).transformAsync(AsyncFunction { tokenResult ->
          if (tokenResult is TaskResult.Success) {
            logger.debug("Attempt to execute bookSelect again")
            val bookSelectTask = BookSelectTask(
              accountID= accountID,
              profileID = profile.id,
              profiles = profiles,
              HTTPClient = this.lsHttp,
              feedEntry = feedEntry.feedEntry,
              bookRegistry = bookRegistry
            )
            submitTask(Callable { bookSelectTask.call() })
          } else {
            //If accessToken refresh fails, return the result that should popup the login
            Futures.immediateFuture(tokenResult)
          }
        }, MoreExecutors.directExecutor())
      } else {
        //In other errors return the normal bookSync answer
        Futures.immediateFuture(taskResult)
      }
    }, MoreExecutors.directExecutor())
  }

  override fun bookRemoveFromSelected(
    accountID: AccountID,
    feedEntry: FeedEntry.FeedEntryOPDS
  ): FluentFuture<TaskResult<*>> {
    val profile = this.profileCurrent()

    return this.submitTask(
      Callable<TaskResult<Unit>> {
        val bookUnselectTask = BookUnselectTask(
          accountID= accountID,
          profileID = profile.id,
          profiles = profiles,
          HTTPClient = this.lsHttp,
          feedEntry = feedEntry.feedEntry,
          bookRegistry = bookRegistry
        )
        bookUnselectTask.call()
      }
    ).transformAsync(AsyncFunction { taskResult ->
      //Check if the result was a need to refresh the accessToken
      //Can be caught with the error code of "accessTokenExpired"
      if (taskResult is TaskResult.Failure<Unit> && taskResult.lastErrorCode == "accessTokenExpired" ) {
        //In order to make the user not have to do anything
        //Try to unselect the book again if the accessToken refresh is successful
        executeProfileAccountAccessTokenRefresh(accountID).transformAsync(AsyncFunction { tokenResult ->
          if (tokenResult is TaskResult.Success) {
            logger.debug("Attempt to execute bookUnselect again")
            val bookUnselectTask = BookUnselectTask(
              accountID= accountID,
              profileID = profile.id,
              profiles = profiles,
              HTTPClient = this.lsHttp,
              feedEntry = feedEntry.feedEntry,
              bookRegistry = bookRegistry
            )
            submitTask(Callable { bookUnselectTask.call() })
          } else {
            //If accessToken refresh fails, return the result that should popup the login
            Futures.immediateFuture(tokenResult)
          }
        }, MoreExecutors.directExecutor())
      } else {
        //In other errors return the normal bookSync answer
        Futures.immediateFuture(taskResult)
      }
    }, MoreExecutors.directExecutor())
  }

  override fun profileAnyIsCurrent(): Boolean =
    this.profiles.currentProfile().isSome

  /**
   * Perform an unchecked (but safe) cast of the given map type. The cast is safe because
   * `V <: VB`.
   */

  private fun <K, VB, V : VB> castMap(m: SortedMap<K, V>): SortedMap<K, VB> {
    return m as SortedMap<K, VB>
  }

  companion object {

    fun createFromServiceDirectory(
      services: ServiceDirectoryType,
      executorService: ExecutorService,
      accountEvents: Subject<AccountEvent>,
      profileEvents: Subject<ProfileEvent>,
      cacheDirectory: File
    ): Controller {
      return Controller(
        cacheDirectory = cacheDirectory,
        accountEvents = accountEvents,
        profileEvents = profileEvents,
        services = services,
        taskExecutor = MoreExecutors.listeningDecorator(executorService)
      )
    }
  }
}
