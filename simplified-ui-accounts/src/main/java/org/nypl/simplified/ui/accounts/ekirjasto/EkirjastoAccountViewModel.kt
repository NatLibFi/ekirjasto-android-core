package org.nypl.simplified.ui.accounts.ekirjasto

import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import org.librarysimplified.documents.DocumentStoreType
import org.librarysimplified.documents.DocumentType
import org.librarysimplified.documents.internal.SimpleDocument
import org.librarysimplified.services.api.Services
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.accounts.api.AccountEventLoginStateChanged
import org.nypl.simplified.accounts.api.AccountEventUpdated
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountLoginState
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.bookmarks.api.BookmarkEvent
import org.nypl.simplified.bookmarks.api.BookmarkServiceType
import org.nypl.simplified.bookmarks.api.BookmarkSyncEnableStatus
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.taskrecorder.api.TaskStep
import org.nypl.simplified.ui.accounts.AccountDetailEvent
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.slf4j.LoggerFactory

class EkirjastoAccountViewModel(
  private val accountId: AccountID,
  private val listener: FragmentListenerType<AccountDetailEvent>,
  private val application: Application,
) : AndroidViewModel(application) {
  private val services =
    Services.serviceDirectory()

  private val profilesController =
    services.requireService(ProfilesControllerType::class.java)

  private val bookmarkService =
    services.requireService(BookmarkServiceType::class.java)

  private val logger = LoggerFactory.getLogger(EkirjastoAccountViewModel::class.java)
  val documents = services.requireService(DocumentStoreType::class.java)

  /**
   * Logging in was explicitly requested. This is tracked in order to allow for optionally
   * closing the account fragment on successful logins.
   */

  @Volatile
  private var loginExplicitlyRequested: Boolean = false

  private val subscriptions = CompositeDisposable()

  private val accountLiveMutable: MutableLiveData<AccountType> =
    MutableLiveData(
      this.profilesController
        .profileCurrent()
        .account(this.accountId)
    )

  val accountLive: LiveData<AccountType> =
    this.accountLiveMutable

  val account: AccountType =
    this.accountLive.value!!

  val authenticationDescription: AccountProviderAuthenticationDescription.Ekirjasto
    get() = this.account.provider.authentication as AccountProviderAuthenticationDescription.Ekirjasto


  /**
   * A live data element that tracks the status of the bookmark syncing switch for the
   * current account.
   */

  private val accountSyncingSwitchStatusMutable: MutableLiveData<BookmarkSyncEnableStatus> =
    MutableLiveData(this.bookmarkService.bookmarkSyncStatus(account.id))

  val accountSyncingSwitchStatus: LiveData<BookmarkSyncEnableStatus> =
    this.accountSyncingSwitchStatusMutable

  val appVersion: String by lazy {
    try {
      val context = this.getApplication<Application>()
      val pkgManager = context.packageManager
      val pkgInfo = pkgManager.getPackageInfo(context.packageName, 0)
      val versionName = pkgInfo.versionName
      val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo).toString()
      "$versionName (${versionCode})"
    } catch (e: PackageManager.NameNotFoundException) {
      "Unknown"
    }
  }

  init {
    this.subscriptions.add(
      this.profilesController.accountEvents()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(this::onAccountEvent)
    )
    this.subscriptions.add(
      this.bookmarkService.bookmarkEvents
        .ofType(BookmarkEvent.BookmarkSyncSettingChanged::class.java)
        .filter { event -> event.accountID == this.accountId }
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(this::onBookmarkEvent)
    )
  }

  private fun onBookmarkEvent(event: BookmarkEvent.BookmarkSyncSettingChanged) {
    this.accountSyncingSwitchStatusMutable.postValue(event.status)
  }

  private fun onAccountEvent(accountEvent: AccountEvent) {
    when (accountEvent) {
      is AccountEventUpdated -> {
        logger.debug("Account Updated")
        if (accountEvent.accountID == this.accountId) {
          this.handleAccountUpdated(accountEvent)
        }
      }
      is AccountEventLoginStateChanged -> {
        logger.debug("Login State Updated: {}",accountEvent.state)
        if (accountEvent.accountID == this.accountId) {
          this.handleLoginStateChanged(accountEvent)
        }
      }
    }
  }

  private fun handleAccountUpdated(event: AccountEventUpdated) {
    this.accountLiveMutable.value = this.account

    /*
     * Synthesize a bookmark event so that we fetch up-to-date values if the account
     * logs in or out.
     */

    this.onBookmarkEvent(
      BookmarkEvent.BookmarkSyncSettingChanged(
        accountID = event.accountID,
        status = this.bookmarkService.bookmarkSyncStatus(event.accountID)
      )
    )
  }

  private fun handleLoginStateChanged(event: AccountEventLoginStateChanged) {
    this.accountLiveMutable.value = this.account

    if (this.loginExplicitlyRequested) {
      when (event.state) {
        is AccountLoginState.AccountLoggedIn -> {
          // Scheduling explicit close of account fragment
          this.loginExplicitlyRequested = false
          this.listener.post(AccountDetailEvent.LoginSucceeded)
        }
        is AccountLoginState.AccountLoginFailed -> {
          this.loginExplicitlyRequested = false
        }
        else -> {
          // Doing nothing
        }
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    this.subscriptions.clear()
  }

  val buildConfig =
    services.requireService(BuildConfigurationServiceType::class.java)

  val eula: DocumentType? =
    this.account.provider.eula?.let { SimpleDocument(it.toURL()) }

  val privacyPolicy: DocumentType? =
    this.account.provider.privacyPolicy?.let { SimpleDocument(it.toURL()) }

  val licenses: DocumentType? =
    this.account.provider.license?.let { SimpleDocument(it.toURL()) }

  /**
   * Logging out was requested. This is tracked in order to allow for
   * clearing the Barcode and PIN fields after the request has completed.
   */

  var pendingLogout: Boolean = false

  /**
   * Enable/disable bookmark syncing.
   */

  fun enableBookmarkSyncing(enabled: Boolean) {
    this.logger.debug("EnableBookmarkSyncing: $enabled")
    this.bookmarkService.bookmarkSyncEnable(this.accountId, enabled)
  }

  fun tryLogin(request: ProfileAccountLoginRequest) {
    this.loginExplicitlyRequested = true
    this.profilesController.profileAccountLogin(request)
  }

  fun tryAccessTokenRefresh(request: ProfileAccountLoginRequest.EkirjastoAccessTokenRefresh) {
    logger.debug("OLD TOKEN {}", request.accessToken)
    //Launch the refresh from the main controller of the app
    //Can add reaction here if wanted, like a popup
    this.profilesController.profileAccountAccessTokenRefresh(request)
  }

  fun tryLogout() {
    this.pendingLogout = true

    // update the login state here so the UI is changed and the user is aware
    // that something's happening
    if (this.account.loginState is AccountLoginState.AccountLoggedIn ||
      this.account.loginState is AccountLoginState.AccountLogoutFailed
    ) {
      this.logger.debug("Account Logging Out")
      this.account.setLoginState(
        AccountLoginState.AccountLoggingOut(
          this.account.loginState.credentials!!,
          ""
        )
      )
    } else {
      this.logger.warn("Invalid AccountLoginState for logout: {}",this.account.loginState)
    }

    this.profilesController.profileAccountLogout(this.accountId)
  }

  fun openErrorPage(taskSteps: List<TaskStep>) {
    val parameters =
      ErrorPageParameters(
        emailAddress = this.buildConfig.supportErrorReportEmailAddress,
        body = "",
        subject = "[simplye-error-report]",
        attributes = sortedMapOf(),
        taskSteps = taskSteps,
        popPrevious = true
      )

    this.listener.post(AccountDetailEvent.OpenErrorPage(parameters))
  }
}
