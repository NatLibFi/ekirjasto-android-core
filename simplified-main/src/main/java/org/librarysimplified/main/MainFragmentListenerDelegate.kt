package org.librarysimplified.main

import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import fi.kansalliskirjasto.ekirjasto.magazines.MagazinesEvent
import io.reactivex.disposables.CompositeDisposable
import org.librarysimplified.ui.catalog.CatalogBookDetailEvent
import org.librarysimplified.ui.catalog.CatalogBookDetailFragment
import org.librarysimplified.ui.catalog.CatalogBookDetailFragmentParameters
import org.librarysimplified.ui.catalog.CatalogFeedArguments
import org.librarysimplified.ui.catalog.CatalogFeedEvent
import org.librarysimplified.ui.catalog.CatalogFeedFragment
import org.librarysimplified.ui.catalog.saml20.CatalogSAML20Event
import org.librarysimplified.ui.login.LoginMainFragment
import org.librarysimplified.ui.navigation.tabs.TabbedNavigator
import org.librarysimplified.viewer.preview.BookPreviewActivity
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.listeners.api.ListenerRepository
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.accounts.AccountCardCreatorFragment
import org.nypl.simplified.ui.accounts.AccountCardCreatorParameters
import org.nypl.simplified.ui.accounts.AccountDetailEvent
import org.nypl.simplified.ui.accounts.AccountFragmentParameters
import org.nypl.simplified.ui.accounts.AccountListEvent
import org.nypl.simplified.ui.accounts.AccountListFragment
import org.nypl.simplified.ui.accounts.AccountListFragmentParameters
import org.nypl.simplified.ui.accounts.AccountListRegistryEvent
import org.nypl.simplified.ui.accounts.AccountListRegistryFragment
import org.nypl.simplified.ui.accounts.AccountPickerEvent
import org.nypl.simplified.ui.accounts.ekirjasto.EKirjastoAccountFragment
import org.nypl.simplified.ui.accounts.ekirjasto.PreferencesEvent
import org.nypl.simplified.ui.accounts.ekirjasto.PreferencesFragment
import org.nypl.simplified.ui.accounts.ekirjasto.DependentsFragment
import org.nypl.simplified.ui.accounts.ekirjasto.passkey.AccountEkirjastoPasskeyFragmentParameters
import org.nypl.simplified.ui.accounts.ekirjasto.suomifi.AccountEkirjastoSuomiFiEvent
import org.nypl.simplified.ui.accounts.ekirjasto.suomifi.AccountEkirjastoSuomiFiFragment
import org.nypl.simplified.ui.accounts.ekirjasto.suomifi.AccountEkirjastoSuomiFiFragmentParameters
import org.nypl.simplified.ui.accounts.ekirjasto.suomifi.EkirjastoLoginMethod
import org.nypl.simplified.ui.accounts.saml20.AccountSAML20Event
import org.nypl.simplified.ui.accounts.saml20.AccountSAML20Fragment
import org.nypl.simplified.ui.accounts.saml20.AccountSAML20FragmentParameters
import org.nypl.simplified.ui.errorpage.ErrorPageEvent
import org.nypl.simplified.ui.errorpage.ErrorPageFragment
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.nypl.simplified.ui.settings.SettingsCustomOPDSFragment
import org.nypl.simplified.ui.settings.SettingsDebugEvent
import org.nypl.simplified.ui.settings.SettingsDebugFragment
import org.nypl.simplified.ui.settings.SettingsDocumentViewerEvent
import org.nypl.simplified.ui.settings.SettingsDocumentViewerFragment
import org.nypl.simplified.ui.settings.SettingsMainEvent
import org.nypl.simplified.viewer.api.Viewers
import org.nypl.simplified.viewer.spi.ViewerPreferences
import org.slf4j.LoggerFactory
import java.net.URL

internal class MainFragmentListenerDelegate(
  private val fragment: Fragment,
  private val listenerRepository: ListenerRepository<MainFragmentListenedEvent, MainFragmentState>,
  private val navigator: TabbedNavigator,
  private val profilesController: ProfilesControllerType,
  private val settingsConfiguration: BuildConfigurationServiceType
) : LifecycleObserver {
  private val logger =
    LoggerFactory.getLogger(MainFragmentListenerDelegate::class.java)

  private val subscriptions =
    CompositeDisposable()

  @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
  fun onCreate() {
    this.configureToolbar()

    val activity = this.fragment.requireActivity() as AppCompatActivity

    activity.onBackPressedDispatcher.addCallback(this.fragment) {
      if (navigator.popBackStack()) {
        //Use the current fragment as Main Fragment so we get
        //to use the showTabs() function
        val loginFragment = fragment as MainFragment
        //Ensure the tabs are shown when pressed back
        //Only changes something if the shown view is the login fragment where there is no tabs visible
        loginFragment.showBottomNavigationMenu()
        return@addCallback
      }

      try {
        isEnabled = false
        activity.onBackPressed()
      } finally {
        isEnabled = true
      }
    }
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_START)
  fun onStart() {
    this.listenerRepository.registerHandler(this::handleEvent)

    this.navigator.infoStream
      .subscribe { action ->
        this.logger.debug(action.toString())
        this.onFragmentTransactionCompleted()
      }
      .let { subscriptions.add(it) }
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
  fun onStop() {
    this.listenerRepository.unregisterHandler()
    subscriptions.clear()
  }

  private fun onFragmentTransactionCompleted() {
    val isRoot = (0 == this.navigator.backStackSize())
    this.logger.debug(
      "controller stack size changed [{}, isRoot={}]", this.navigator.backStackSize(), isRoot
    )
    configureToolbar()
  }

  private fun configureToolbar() {
    val isRoot = (0 == this.navigator.backStackSize())
    val activity = this.fragment.requireActivity() as AppCompatActivity
    activity.supportActionBar?.apply {
      setHomeAsUpIndicator(null)
      setHomeActionContentDescription(null)
      setDisplayHomeAsUpEnabled(!isRoot)
    }
  }

  private fun handleEvent(
    event: MainFragmentListenedEvent,
    state: MainFragmentState
  ): MainFragmentState {
    return when (event) {
      is MainFragmentListenedEvent.CatalogSAML20Event ->
        this.handleCatalogSAML20Event(event.event, state)

      is MainFragmentListenedEvent.CatalogFeedEvent ->
        this.handleCatalogFeedEvent(event.event, state)

      is MainFragmentListenedEvent.CatalogBookDetailEvent ->
        this.handleCatalogBookDetailEvent(event.event, state)

      is MainFragmentListenedEvent.SettingsMainEvent ->
        this.handleSettingsMainEvent(event.event, state)

      is MainFragmentListenedEvent.SettingsDebugEvent ->
        this.handleSettingsDebugEvent(event.event, state)

      is MainFragmentListenedEvent.SettingsDocumentViewerEvent ->
        this.handleSettingsDocumentViewerEvent(event.event, state)

      is MainFragmentListenedEvent.AccountListRegistryEvent ->
        this.handleAccountListRegistryEvent(event.event, state)

      is MainFragmentListenedEvent.AccountListEvent ->
        this.handleAccountListEvent(event.event, state)

      is MainFragmentListenedEvent.AccountDetailEvent ->
        this.handleAccountEvent(event.event, state)

      is MainFragmentListenedEvent.AccountSAML20Event ->
        this.handleAccountSAML20Event(event.event, state)

      is MainFragmentListenedEvent.AccountEkirjastoSuomiFiEvent ->
        this.handleAccountEkirjastoSuomiFiEvent(event.event, state)

      is MainFragmentListenedEvent.AccountPickerEvent ->
        this.handleAccountPickerEvent(event.event, state)

      is MainFragmentListenedEvent.PreferencesEvent ->
        this.handlePreferencesEvent(event.event, state)

      is MainFragmentListenedEvent.MagazinesEvent ->
        this.handleMagazinesEvent(event.event, state)

      is MainFragmentListenedEvent.ErrorPageEvent ->
        this.handleErrorPageEvent(event.event, state)
    }
  }

  private fun handlePreferencesEvent(
    event: PreferencesEvent,
    state: MainFragmentState): MainFragmentState {
    return when (event) {
      is PreferencesEvent.GoUpwards -> {
        this.goUpwardsInSettings()
        state
      }
    }
  }

  private fun handleCatalogSAML20Event(
    event: CatalogSAML20Event,
    state: MainFragmentState
  ): MainFragmentState {
    return when (event) {
      is CatalogSAML20Event.OpenErrorPage -> {
        this.openErrorPage(event.parameters)
        state
      }

      CatalogSAML20Event.LoginSucceeded -> {
        this.popBackStack()
        state
      }
    }
  }

  private fun handleCatalogFeedEvent(
    event: CatalogFeedEvent,
    state: MainFragmentState
  ): MainFragmentState {
    return when (event) {
      is CatalogFeedEvent.LoginRequired -> {
        this.openLogin()
        MainFragmentState.CatalogWaitingForLogin
      }

      is CatalogFeedEvent.OpenErrorPage -> {
        this.openErrorPage(event.parameters)
        state
      }

      is CatalogFeedEvent.OpenViewer -> {
        this.openViewer(event.book, event.format)
        state
      }

      is CatalogFeedEvent.OpenBookDetail -> {
        this.openBookDetail(event.feedArguments, event.opdsEntry)
        state
      }

      is CatalogFeedEvent.OpenFeed -> {
        this.openFeed(event.feedArguments)
        state
      }

      is CatalogFeedEvent.GoUpwards -> {
        this.goUpwards()
        state
      }
      is CatalogFeedEvent.RefreshViews -> {
        this.navigator.popToRoot()
        state
      }
    }
  }

  private fun handleCatalogBookDetailEvent(
    event: CatalogBookDetailEvent,
    state: MainFragmentState
  ): MainFragmentState {
    return when (event) {
      is CatalogBookDetailEvent.LoginRequired -> {
        this.openLogin()
        MainFragmentState.BookDetailsWaitingForLogin
      }

      is CatalogBookDetailEvent.OpenErrorPage -> {
        this.openErrorPage(event.parameters)
        state
      }

      is CatalogBookDetailEvent.OpenViewer -> {
        this.openViewer(event.book, event.format)
        state
      }

      is CatalogBookDetailEvent.OpenFeed -> {
        this.openFeed(event.feedArguments)
        state
      }

      is CatalogBookDetailEvent.OpenBookDetail -> {
        this.openBookDetail(event.feedArguments, event.opdsEntry)
        state
      }

      is CatalogBookDetailEvent.OpenPreviewViewer -> {
        this.openPreviewViewer(event.feedEntry)
        state
      }

      CatalogBookDetailEvent.GoUpwards -> {
        this.goUpwards()
        state
      }
    }
  }

  private fun openPreviewViewer(feedEntry: FeedEntry.FeedEntryOPDS) {
    BookPreviewActivity.startActivity(
      fragment.requireActivity(),
      feedEntry
    )
  }

  private fun handleAccountListRegistryEvent(
    event: AccountListRegistryEvent,
    state: MainFragmentState
  ): MainFragmentState {
    return when (event) {
      is AccountListRegistryEvent.AccountCreated -> {
        this.setMostRecentAccount(event.accountID)
        this.popBackStack()
        this.openCatalog()
        state
      }

      is AccountListRegistryEvent.OpenErrorPage -> {
        this.openErrorPage(event.parameters)
        state
      }

      is AccountListRegistryEvent.GoUpwards -> {
        this.goUpwards()
        state
      }
    }
  }

  private fun setMostRecentAccount(accountID: AccountID) {
    this.profilesController.profileUpdate { description ->
      description.copy(preferences = description.preferences.copy(mostRecentAccount = accountID))
    }.get()
  }

  private fun handleAccountListEvent(
    event: AccountListEvent,
    state: MainFragmentState
  ): MainFragmentState {
    return when (event) {
      is AccountListEvent.AccountSelected -> {
        this.openSettingsAccount(
          accountID = event.accountID,
          comingFromBookLoanRequest = false,
          comingFromDeepLink = event.comingFromDeepLink,
          barcode = event.barcode
        )
        state
      }

      AccountListEvent.AddAccount -> {
        this.openAccountRegistry(tab = org.librarysimplified.ui.tabs.R.id.tabSettings)
        state
      }

      is AccountListEvent.OpenErrorPage -> {
        this.openErrorPage(event.parameters)
        state
      }

      AccountListEvent.GoUpwards -> {
        this.goUpwards()
        state
      }
    }
  }

  private fun handleAccountEvent(
    event: AccountDetailEvent,
    state: MainFragmentState
  ): MainFragmentState {
    return when (event) {
      AccountDetailEvent.LoginSucceeded ->
        when (state) {
          is MainFragmentState.CatalogWaitingForLogin,
          is MainFragmentState.BookDetailsWaitingForLogin -> {
            this.navigator.popBackStack()
            MainFragmentState.EmptyState
          }
          else -> {
            state
          }
        }

      is AccountDetailEvent.OpenErrorPage -> {
        this.openErrorPage(event.parameters)
        state
      }

      is AccountDetailEvent.OpenSAML20Login -> {
        this.openSAML20Login(event.account, event.authenticationDescription)
        state
      }

      is AccountDetailEvent.OpenEkirjastoSuomiFiLogin -> {
        this.openEkirjastoLogin(event.account, event.authenticationDescription, event.loginMethod)
        state
      }

      is AccountDetailEvent.OpenEkirjastoPasskeyLogin -> {
        this.openEkirjastoLogin(event.account, event.authenticationDescription, event.loginMethod)
        state
      }

      is AccountDetailEvent.OpenDocViewer -> {
        this.openDocViewer(event.title, event.url)
        state
      }

      is AccountDetailEvent.OpenWebView -> {
        this.openCardCreatorWebView(event.parameters)
        state
      }

      is AccountDetailEvent.GoUpwards -> {
        this.goUpwards()
        state
      }

      is AccountDetailEvent.OpenPreferences -> {
        this.openPreferences()
        state
      }

      is AccountDetailEvent.OpenDependentInvite -> {
        this.openDependentPage()
        state
      }
    }
  }

  private fun openPreferences() {
    this.navigator.addFragment(
      fragment = PreferencesFragment(),
      tab = org.librarysimplified.ui.tabs.R.id.tabSettings
    )
  }

  private fun handleAccountSAML20Event(
    event: AccountSAML20Event,
    state: MainFragmentState
  ): MainFragmentState {
    return when (event) {
      AccountSAML20Event.AccessTokenObtained -> {
        this.popBackStack()
        state
      }

      is AccountSAML20Event.OpenErrorPage -> {
        this.openErrorPage(event.parameters)
        state
      }
    }
  }

  private fun handleAccountEkirjastoSuomiFiEvent(
    event: AccountEkirjastoSuomiFiEvent,
    state: MainFragmentState
  ): MainFragmentState {
    return when (event) {
      AccountEkirjastoSuomiFiEvent.AccessTokenObtained,
      AccountEkirjastoSuomiFiEvent.PasskeySuccessful -> {
        this.popBackStack()
        state
      }

      is AccountEkirjastoSuomiFiEvent.OpenErrorPage -> {
        this.openErrorPage(event.parameters)
        state
      }
      AccountEkirjastoSuomiFiEvent.Cancel -> {
        this.goUpwards()
        state
      }
    }
  }

  private fun handleAccountPickerEvent(
    event: AccountPickerEvent,
    state: MainFragmentState
  ): MainFragmentState {
    return when (event) {
      is AccountPickerEvent.AccountSelected -> {
        // TODO: this should work without this for now
        state
      }

      AccountPickerEvent.AddAccount -> {
        this.openAccountRegistry(tab = org.librarysimplified.ui.tabs.R.id.tabCatalog)
        state
      }
    }
  }

  private fun handleMagazinesEvent(
    event: MagazinesEvent,
    state: MainFragmentState
  ): MainFragmentState {
    logger.debug("handleMagazinesEvent({})", event)
    return when (event) {
      MagazinesEvent.GoUpwards -> {
        this.goUpwards()
        state
      }
      MagazinesEvent.LoginRequired -> {
        openLogin()
        state
      }
      else -> state
    }
  }

  private fun handleErrorPageEvent(
    event: ErrorPageEvent,
    state: MainFragmentState
  ): MainFragmentState {
    return when (event) {
      ErrorPageEvent.GoUpwards -> {
        this.goUpwards()
        state
      }
    }
  }

  private fun handleSettingsMainEvent(
    event: SettingsMainEvent,
    state: MainFragmentState
  ): MainFragmentState {
    return when (event) {
      is SettingsMainEvent.OpenAbout -> {
        this.openSettingsAbout(event.title, event.url)
        state
      }

      SettingsMainEvent.OpenAccountList -> {
        this.openSettingsAccounts()
        state
      }

      is SettingsMainEvent.OpenAcknowledgments -> {
        this.openSettingsAcknowledgements(event.title, event.url)
        state
      }

      SettingsMainEvent.OpenDebugOptions -> {
        this.openSettingsVersion()
        state
      }

      is SettingsMainEvent.OpenEULA -> {
        this.openSettingsEULA(event.title, event.url)
        state
      }

      is SettingsMainEvent.OpenFAQ -> {
        this.openSettingsFaq(event.title, event.url)
        state
      }

      is SettingsMainEvent.OpenLicense -> {
        this.openSettingsLicense(event.title, event.url)
        state
      }

      is SettingsMainEvent.OpenPrivacy -> {
        this.openSettingsPrivacy(event.title, event.url)
        state
      }

      is SettingsMainEvent.OpenFeedback -> {
        this.openSettingsFeedback(event.title, event.url)
        state
      }

      is SettingsMainEvent.OpenAccessibilityStatement -> {
        this.openSettingsAccessibilityStatement(event.title, event.url)
        state
      }
    }
  }

  private fun handleSettingsDebugEvent(
    event: SettingsDebugEvent,
    state: MainFragmentState
  ): MainFragmentState {
    return when (event) {
      SettingsDebugEvent.OpenCustomOPDS -> {
        this.openSettingsCustomOPDS()
        state
      }

      is SettingsDebugEvent.OpenErrorPage -> {
        this.openErrorPage(event.parameters)
        state
      }

      SettingsDebugEvent.GoUpwards -> {
        this.goUpwards()
        state
      }
    }
  }

  private fun handleSettingsDocumentViewerEvent(
    event: SettingsDocumentViewerEvent,
    state: MainFragmentState
  ): MainFragmentState {
    return when (event) {
      SettingsDocumentViewerEvent.GoUpwards -> {
        this.goUpwards()
        state
      }
    }
  }

  private fun popBackStack() {
    this.navigator.popBackStack()
  }

  private fun openSettingsAbout(title: String, url: String) {
    this.navigator.addFragment(
      fragment = SettingsDocumentViewerFragment.create(title, url),
      tab = org.librarysimplified.ui.tabs.R.id.tabSettings
    )
  }

  private fun openSettingsAccounts() {
    this.navigator.addFragment(
      fragment = AccountListFragment.create(
        AccountListFragmentParameters(
          shouldShowLibraryRegistryMenu = this.settingsConfiguration.allowAccountsRegistryAccess,
          accountID = null,
          barcode = null,
          comingFromDeepLink = false
        )
      ),
      tab = org.librarysimplified.ui.tabs.R.id.tabSettings
    )
  }
  private fun openDependentPage() {
    this.navigator.addFragment(
      fragment = DependentsFragment(),
      tab = org.librarysimplified.ui.tabs.R.id.tabSettings
    )
  }

  private fun openSettingsAcknowledgements(title: String, url: String) {
    this.navigator.addFragment(
      fragment = SettingsDocumentViewerFragment.create(title, url),
      tab = org.librarysimplified.ui.tabs.R.id.tabSettings
    )
  }

  private fun openSettingsEULA(title: String, url: String) {
    this.navigator.addFragment(
      fragment = SettingsDocumentViewerFragment.create(title, url),
      tab = org.librarysimplified.ui.tabs.R.id.tabSettings
    )
  }

  private fun openSettingsFaq(title: String, url: String) {
    this.navigator.addFragment(
      fragment = SettingsDocumentViewerFragment.create(title, url),
      tab = org.librarysimplified.ui.tabs.R.id.tabSettings
    )
  }

  private fun openSettingsLicense(title: String, url: String) {
    this.navigator.addFragment(
      fragment = SettingsDocumentViewerFragment.create(title, url),
      tab = org.librarysimplified.ui.tabs.R.id.tabSettings
    )
  }

  private fun openSettingsPrivacy(title: String, url: String) {
    this.navigator.addFragment(
      fragment = SettingsDocumentViewerFragment.create(title, url),
      tab = org.librarysimplified.ui.tabs.R.id.tabSettings
    )
  }

  private fun openSettingsFeedback(title: String, url: String) {
    this.navigator.addFragment(
      fragment = SettingsDocumentViewerFragment.create(title, url),
      tab = org.librarysimplified.ui.tabs.R.id.tabSettings
    )
  }

  private fun openSettingsAccessibilityStatement(title: String, url: String) {
    this.navigator.addFragment(
      fragment = SettingsDocumentViewerFragment.create(title, url),
      tab = org.librarysimplified.ui.tabs.R.id.tabSettings
    )
  }



  private fun openSettingsVersion() {
    this.navigator.addFragment(
      fragment = SettingsDebugFragment(),
      tab = org.librarysimplified.ui.tabs.R.id.tabSettings
    )
  }

  private fun openSettingsCustomOPDS() {
    this.navigator.addFragment(
      fragment = SettingsCustomOPDSFragment(),
      tab = org.librarysimplified.ui.tabs.R.id.tabSettings
    )
  }

  private fun openErrorPage(parameters: ErrorPageParameters) {
    if (parameters.popPrevious){
      this.navigator.popBackStack()
    }
    this.navigator.addFragment(
      fragment = ErrorPageFragment.create(parameters),
      tab = this.navigator.currentTab()
    )
  }

  private fun openCardCreatorWebView(parameters: AccountCardCreatorParameters) {
    this.navigator.addFragment(
      fragment = AccountCardCreatorFragment.create(parameters),
      tab = this.navigator.currentTab()
    )
  }

  private fun goUpwards() {
    logger.debug("goUpwards(), stack size: {}", navigator.backStackSize())
    val isRoot = (0 == navigator.backStackSize())
    if (!isRoot) {
      navigator.popBackStack()
    }
    else {
      openCatalog()
    }
  }

  private fun goUpwardsInSettings() {
    logger.debug("goUpwards(), stack size: {}", navigator.backStackSize())
    val isRoot = (0 == navigator.backStackSize())
    if (!isRoot) {
      navigator.popBackStack()
    }
    else {
      openSettingsAccounts()
    }
  }

  private fun openLogin() {
    this.logger.debug("openLogin")
    //Add the login fragment, the tab isn't showing so it can be the current one
    this.navigator.addFragment(
      fragment = LoginMainFragment.create(),
      tab = this.navigator.currentTab()
    )
    //Use the current fragment as Main Fragment so we get
    //to use the hideTabs() function
    val loginFragment = this.fragment as MainFragment
    loginFragment.hideBottomNavigationMenu()
  }

  private fun openSettingsAccount(
    accountID: AccountID,
    comingFromBookLoanRequest: Boolean,
    comingFromDeepLink: Boolean,
    barcode: String?
  ) {
    this.logger.debug("Open Ekirjasto Account: called with comingFromDeepLink: $comingFromDeepLink")
    this.navigator.addFragment(
      fragment = EKirjastoAccountFragment.create(
        AccountFragmentParameters(
          accountID = accountID,
          showPleaseLogInTitle = comingFromBookLoanRequest,
          hideToolbar = comingFromDeepLink,
          barcode = barcode
        )
      ),
      tab = this.navigator.currentTab()
    )
  }

  private fun openSAML20Login(
    account: AccountID,
    authenticationDescription: AccountProviderAuthenticationDescription.SAML2_0
  ) {
    this.navigator.addFragment(
      fragment = AccountSAML20Fragment.create(
        AccountSAML20FragmentParameters(
          accountID = account,
          authenticationDescription = authenticationDescription
        )
      ),
      tab = this.navigator.currentTab()
    )
  }

  // Finland
  private fun openEkirjastoLogin(
    account: AccountID,
    authenticationDescription: AccountProviderAuthenticationDescription.Ekirjasto,
    loginMethod: EkirjastoLoginMethod,
  ) {
    this.logger.debug("Open Ekirjasto Login. loginMethod=$loginMethod")
    when (loginMethod) {
      is EkirjastoLoginMethod.SuomiFi -> {
        this.navigator.addFragment(
          fragment = AccountEkirjastoSuomiFiFragment.create(
            AccountEkirjastoSuomiFiFragmentParameters(
              accountID = account,
              authenticationDescription = authenticationDescription
            )
          ),
          tab = this.navigator.currentTab()
        )
      }
      is EkirjastoLoginMethod.Passkey -> {
        this.navigator.addFragment(
          fragment = org.nypl.simplified.ui.accounts.ekirjasto.passkey.AccountEkirjastoPasskeyFragment.create(
            AccountEkirjastoPasskeyFragmentParameters(
              accountID = account,
              authenticationDescription = authenticationDescription,
              loginMethod
            )
          ),
          tab = this.navigator.currentTab()
        )
      }
    }
  }

  private fun openDocViewer(
    title: String,
    url: URL
  ) {
    this.navigator.addFragment(
      fragment = SettingsDocumentViewerFragment.create(title, url.toString()),
      tab = this.navigator.currentTab()
    )
  }

  private fun openBookDetail(
    feedArguments: CatalogFeedArguments,
    entry: FeedEntry.FeedEntryOPDS
  ) {
    this.navigator.addFragment(
      fragment = CatalogBookDetailFragment.create(
        CatalogBookDetailFragmentParameters(
          feedEntry = entry,
          feedArguments = feedArguments
        )
      ),
      tab = this.navigator.currentTab()
    )
  }

  private fun openFeed(feedArguments: CatalogFeedArguments) {
    this.navigator.addFragment(
      fragment = CatalogFeedFragment.create(feedArguments),
      tab = this.navigator.currentTab()
    )
  }

  private fun openAccountRegistry(tab: Int) {
    this.navigator.addFragment(
      fragment = AccountListRegistryFragment(),
      tab = tab
    )
  }

  private fun openCatalog() {
    this.navigator.popBackStack()
    this.navigator.reset(org.librarysimplified.ui.tabs.R.id.tabCatalog, false)
  }

  private fun openViewer(
    book: Book,
    format: BookFormat
  ) {
    val viewerPreferences =
      ViewerPreferences(
        flags = mapOf()
      )

    Viewers.openViewer(
      activity = this.fragment.requireActivity(),
      preferences = viewerPreferences,
      book = book,
      format = format
    )
  }
}
