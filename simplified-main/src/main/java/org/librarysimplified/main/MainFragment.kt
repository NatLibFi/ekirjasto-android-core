package org.librarysimplified.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomnavigation.BottomNavigationItemView
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.reactivex.disposables.CompositeDisposable
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.catalog.saml20.CatalogSAML20Fragment
import org.librarysimplified.ui.catalog.saml20.CatalogSAML20FragmentParameters
import org.librarysimplified.ui.navigation.tabs.TabbedNavigator
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.accounts.api.AccountEventDeletion
import org.nypl.simplified.accounts.api.AccountEventLoginStateChanged
import org.nypl.simplified.accounts.api.AccountEventUpdated
import org.nypl.simplified.accounts.api.AccountLoginState
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.book_registry.BookHoldsUpdateEvent
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookStatusEvent
import org.nypl.simplified.deeplinks.controller.api.DeepLinkEvent
import org.nypl.simplified.deeplinks.controller.api.ScreenID
import org.nypl.simplified.listeners.api.ListenerRepository
import org.nypl.simplified.listeners.api.listenerRepositories
import org.nypl.simplified.profiles.api.ProfileEvent
import org.nypl.simplified.profiles.api.ProfileUpdated
import org.nypl.simplified.ui.accounts.AccountListFragment
import org.nypl.simplified.ui.accounts.AccountListFragmentParameters
import org.nypl.simplified.ui.announcements.AnnouncementsDialog
import org.nypl.simplified.ui.announcements.TipsDialog
import org.slf4j.LoggerFactory
import java.net.URI

/**
 * The main application fragment.
 *
 * Currently, this displays a tabbed view and also displays tips and dialogs on various application
 * events.
 */

class MainFragment : Fragment(R.layout.main_tabbed_host) {

  companion object {

    private val ANNOUNCEMENT_DIALOG_TAG =
      AnnouncementsDialog::class.java.simpleName

    private val TIPS_DIALOG_TAG =
      TipsDialog::class.java.simpleName
  }

  private val logger = LoggerFactory.getLogger(MainFragment::class.java)
  private val subscriptions = CompositeDisposable()

  private val viewModel: MainFragmentViewModel by viewModels(
    factoryProducer = {
      MainFragmentViewModelFactory(
        resources = this.requireActivity().resources
      )
    }
  )

  private val listenerRepo: ListenerRepository<MainFragmentListenedEvent, MainFragmentState> by listenerRepositories()

  private val defaultViewModelFactory: ViewModelProvider.Factory by lazy {
    MainFragmentDefaultViewModelFactory(super.defaultViewModelProviderFactory)
  }

  private lateinit var bottomView: BottomNavigationView
  private lateinit var navigator: TabbedNavigator

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    /*
     * Demand that onOptionsItemSelected be called.
     */

    setHasOptionsMenu(true)

    this.checkForAnnouncements()
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    this.bottomView =
      view.findViewById(R.id.bottomNavigator)

    ViewCompat.setOnApplyWindowInsetsListener(bottomView) {view, insets ->
      val insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      // Apply the insets as a margin to the view. This solution sets
      // only the bottom, left, and right dimensions, but you can apply whichever
      // insets are appropriate to your layout. You can also update the view padding
      // if that's more appropriate.
      view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        leftMargin = insets.left
        bottomMargin = insets.bottom
        rightMargin = insets.right
      }

      // Return CONSUMED if you don't want the window insets to keep passing
      // down to descendant views.
      WindowInsetsCompat.CONSUMED
    }

    /*
     * Hide various tabs based on build configuration and other settings.
     */
    this.setShowHoldsVisibility()

    val settingsItem = this.bottomView.menu.findItem(org.librarysimplified.ui.tabs.R.id.tabSettings)
    settingsItem.isVisible = viewModel.buildConfig.showSettingsTab
    settingsItem.isEnabled = viewModel.buildConfig.showSettingsTab

    // Force bottom tab menu strings to go through Transifex wrapper to actually localize tab titles
    this.bottomView.menu.forEach {
      when (it.itemId) {
        org.librarysimplified.ui.tabs.R.id.tabCatalog   -> it.title = getString(org.librarysimplified.ui.tabs.R.string.tabCatalog)
        org.librarysimplified.ui.tabs.R.id.tabBooks     -> it.title = getString(org.librarysimplified.ui.tabs.R.string.tabBooks)
        org.librarysimplified.ui.tabs.R.id.tabHolds     -> it.title = getString(org.librarysimplified.ui.tabs.R.string.tabHolds)
        org.librarysimplified.ui.tabs.R.id.tabMagazines -> it.title = getString(org.librarysimplified.ui.tabs.R.string.tabMagazines)
        org.librarysimplified.ui.tabs.R.id.tabSettings  -> it.title = getString(org.librarysimplified.ui.tabs.R.string.tabSettings)
      }
    }

    this.navigator =
      TabbedNavigator.create(
        fragment = this,
        fragmentContainerId = R.id.tabbedFragmentHolder,
        navigationView = this.bottomView,
        accountProviders = viewModel.accountProviders,
        profilesController = viewModel.profilesController,
        settingsConfiguration = viewModel.buildConfig,
      )

    lifecycle.addObserver(
      MainFragmentListenerDelegate(
        fragment = this,
        listenerRepository = listenerRepo,
        navigator = this.navigator,
        profilesController = viewModel.profilesController,
        settingsConfiguration = viewModel.buildConfig
      )
    )
  }

  /**
   * Hide bottom navigation menu from the user
   */
  fun hideBottomNavigationMenu() {
    bottomView.visibility = View.GONE
  }

  /**
   * Show user the bottom navigation menu
   */
  fun showBottomNavigationMenu() {
    bottomView.visibility = View.VISIBLE
  }
  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home -> {
        this.navigator.popToRoot()
      }
      else -> super.onOptionsItemSelected(item)
    }
  }

  override fun onStart() {
    super.onStart()

    viewModel.accountEvents
      .subscribe(this::onAccountEvent)
      .let { subscriptions.add(it) }

    viewModel.profileEvents
      .subscribe(this::onProfileEvent)
      .let { subscriptions.add(it) }

    viewModel.registryEvents
      .subscribe(this::onBookStatusEvent)
      .let { subscriptions.add(it) }

    viewModel.deepLinkEvents
      .subscribe(this::onDeepLinkEvent)
      .let { subscriptions.add(it) }

    viewModel.bookHoldEvents
      .subscribe(this::onBookHoldsUpdateEvent)
      .let { subscriptions.add(it) }

    /*
     * Show the Adobe DRM warning dialog if necessary,
     */

    MainAdobeWarnings.showWarningDialogIfNecessary(
      this.requireActivity(),
      Services.serviceDirectory()
    )
  }

  private fun onAccountEvent(event: AccountEvent) {
    when (event) {
      is AccountEventUpdated -> {
        this.checkForAnnouncements()
      }
      is AccountEventLoginStateChanged -> {
        when (event.state) {
          is AccountLoginState.AccountLoggedIn -> {
            //Show tips after logging in
            this.showTipsIfNotDismissed()
          }
          else -> {
            //do nothing
          }
        }
      }

      /*
       * We don't know which fragments on the backstack might refer to accounts that
       * have been deleted so we need to clear the history when an account is deleted.
       * It would be better if we could eliminate specific items from the history, but
       * this is Android...
       */

      is AccountEventDeletion.AccountEventDeletionSucceeded -> {
        try {
          this.navigator.clearHistory()
        } catch (e: Throwable) {
          this.logger.error("could not clear history: ", e)
        }
      }
    }
  }

  private fun onProfileEvent(event: ProfileEvent) {
    when (event) {
      is ProfileUpdated.Succeeded ->
        this.onProfileUpdateSucceeded(event)
    }
  }

  private fun setShowHoldsVisibility() {

    val showHolds = viewModel.showHoldsTab
    //debug
    this.logger.debug("DBGHOLDS BuildConfigurationType=${viewModel.buildConfig.javaClass.canonicalName}")
    this.logger.debug("DBGHOLDS viewModel.showHoldsTab=$showHolds")
    val provider = viewModel.profilesController.profileCurrent().mostRecentAccount().provider
    this.logger.debug("DBGHOLDS accountProvider id=${provider.id}, displayName=${provider.displayName}, supportReservation=${provider.supportsReservations}")
    val holdsItem = this.bottomView.menu.findItem(org.librarysimplified.ui.tabs.R.id.tabHolds)
    holdsItem.isVisible = showHolds
    holdsItem.isEnabled = showHolds
  }

  private fun onProfileUpdateSucceeded(event: ProfileUpdated.Succeeded) {
    val oldAccountId = event.oldDescription.preferences.mostRecentAccount
    val newAccountId = event.newDescription.preferences.mostRecentAccount
    this.logger.debug("oldAccountId={}, newAccountId={}", oldAccountId, newAccountId)

    // Reload the catalog feed, the patron's account preference has changed

    if (oldAccountId != newAccountId) {
      this.navigator.clearHistory()
    }

    this.checkForAnnouncements()
    this.setShowHoldsVisibility()
  }

  private fun onBookStatusEvent(event: BookStatusEvent) {
    when (val status = event.statusNow) {
      is BookStatus.DownloadWaitingForExternalAuthentication -> {
        this.openBookDownloadLogin(status.id, status.downloadURI)
      }

      is BookStatus.DownloadExternalAuthenticationInProgress,
      is BookStatus.Downloading,
      is BookStatus.FailedDownload,
      is BookStatus.FailedLoan,
      is BookStatus.FailedRevoke,
      is BookStatus.Held.HeldInQueue,
      is BookStatus.Held.HeldReady,
      is BookStatus.Holdable,
      is BookStatus.Loanable,
      is BookStatus.Loaned.LoanedDownloaded,
      is BookStatus.Loaned.LoanedNotDownloaded,
      is BookStatus.ReachedLoanLimit,
      is BookStatus.RequestingDownload,
      is BookStatus.RequestingLoan,
      is BookStatus.RequestingRevoke,
      is BookStatus.Revoked,
      null -> {
        // do nothing
      }
    }
  }

  private fun onDeepLinkEvent(event: DeepLinkEvent) {
    if (event.screenID == ScreenID.LOGIN) {
      this.navigator.addFragment(
        fragment = AccountListFragment.create(
          AccountListFragmentParameters(
            shouldShowLibraryRegistryMenu = false,
            accountID = event.accountID,
            barcode = event.barcode,
            comingFromDeepLink = true
          )
        ),
        tab = org.librarysimplified.ui.tabs.R.id.tabSettings
      )
    }
  }

  private fun onBookHoldsUpdateEvent(event: BookHoldsUpdateEvent) {
    val numberOfHolds = event.numberOfHolds
    if (viewModel.showHoldsTab) {
      val bottomNavigationItem =
        this.bottomView.findViewById<BottomNavigationItemView>(org.librarysimplified.ui.tabs.R.id.tabHolds)
      var badgeView =
        bottomNavigationItem.findViewById<View>(org.librarysimplified.ui.tabs.R.id.badgeView)

      if (numberOfHolds > 0) {
        if (badgeView == null) {
          badgeView = LayoutInflater.from(requireContext()).inflate(
            org.librarysimplified.ui.tabs.R.layout.layout_menu_item_badge, bottomNavigationItem, false
          )
          bottomNavigationItem.addView(badgeView)
        }

        val badgeNumber = (badgeView as? ViewGroup)?.findViewById<TextView>(
          org.librarysimplified.ui.tabs.R.id.badgeNumber)
        badgeNumber?.text = numberOfHolds.toString()
      }

      badgeView?.isVisible = numberOfHolds > 0
    }
  }

  private fun checkForAnnouncements() {
    val currentProfile = this.viewModel.profilesController.profileCurrent()
    val mostRecentAccountId = currentProfile.preferences().mostRecentAccount
    val mostRecentAccount = currentProfile.account(mostRecentAccountId)
    val acknowledged =
      mostRecentAccount.preferences.announcementsAcknowledged.toSet()
    val notYetAcknowledged =
      mostRecentAccount.provider.announcements.filter { !acknowledged.contains(it.id) }

    if (notYetAcknowledged.isNotEmpty() &&
      this.childFragmentManager.findFragmentByTag(ANNOUNCEMENT_DIALOG_TAG) == null
    ) {
      this.showAnnouncementsDialog()
    }
  }

  private fun showAnnouncementsDialog() {
    AnnouncementsDialog().showNow(this.childFragmentManager, ANNOUNCEMENT_DIALOG_TAG)
  }

  private fun showTipsIfNotDismissed() {
    val appCache = AppCache(this.requireContext())
    if (!appCache.isTipsDismissed()) {
      showTipsDialog()
    }
  }

  private fun showTipsDialog() {
    TipsDialog().showNow(this.childFragmentManager, TIPS_DIALOG_TAG)
  }

  private fun openBookDownloadLogin(
    bookID: BookID,
    downloadURI: URI
  ) {
    this.navigator.addFragment(
      fragment = CatalogSAML20Fragment.create(
        CatalogSAML20FragmentParameters(
          bookID = bookID,
          downloadURI = downloadURI
        )
      ),
      tab = this.navigator.currentTab()
    )
  }

  override fun onStop() {
    super.onStop()
    this.subscriptions.clear()
  }

  override val defaultViewModelProviderFactory: ViewModelProvider.Factory
    get() = this.defaultViewModelFactory
}
