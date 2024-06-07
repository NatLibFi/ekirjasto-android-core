package org.nypl.simplified.ui.accounts.ekirjasto

import android.os.Bundle
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import fi.kansalliskirjasto.ekirjasto.util.LanguageUtil
import io.reactivex.disposables.CompositeDisposable
import org.librarysimplified.documents.DocumentType
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.accounts.R
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountLoginState
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggedIn
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggingIn
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggingInWaitingForExternalAuthentication
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoginFailed
import org.nypl.simplified.accounts.api.AccountLoginState.AccountNotLoggedIn
import org.nypl.simplified.android.ktx.supportActionBar
import org.nypl.simplified.bookmarks.api.BookmarkSyncEnableResult
import org.nypl.simplified.bookmarks.api.BookmarkSyncEnableStatus
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.listeners.api.fragmentListeners
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest
import org.nypl.simplified.ui.accounts.AccountDetailEvent
import org.nypl.simplified.ui.accounts.AccountFragmentParameters
import org.nypl.simplified.ui.accounts.ekirjasto.suomifi.EkirjastoLoginMethod
import org.slf4j.LoggerFactory
import org.thepalaceproject.theme.core.PalaceToolbar

class EKirjastoAccountFragment : Fragment(R.layout.account_ekirjasto){
  private val logger =
    LoggerFactory.getLogger(EKirjastoAccountFragment::class.java)
  private val subscriptions: CompositeDisposable =
    CompositeDisposable()
  private val listener: FragmentListenerType<AccountDetailEvent> by fragmentListeners()
  private val parameters: AccountFragmentParameters by lazy {
    this.requireArguments()[PARAMETERS_ID] as AccountFragmentParameters
  }

  private val services = Services.serviceDirectory()

  private val viewModel: EkirjastoAccountViewModel by viewModels(
    factoryProducer = {
      EkirjastoAccountViewModelFactory(
        account = this.parameters.accountID,
        listener = this.listener,
        application = this.requireActivity().application
      )
    }
  )

  //elements
  private lateinit var buttonLogout: Button

  private lateinit var buttonLoginSuomiFi: Button

  private lateinit var buttonLoginPasskey: Button
  private lateinit var buttonRegisterPasskey: Button
  private lateinit var eulaStatement: TextView
  private lateinit var syncBookmarks: ConstraintLayout
  private lateinit var buttonFeedback: Button
  private lateinit var buttonPrivacyPolicy: Button
  private lateinit var buttonUserAgreement: Button
  private lateinit var buttonAccessibilityStatement: Button
  private lateinit var buttonLicenses: Button
  private lateinit var buttonFaq: Button
  private lateinit var versionText: TextView
  private lateinit var bookmarkSyncProgress: ProgressBar
  private lateinit var bookmarkSyncCheck: SwitchCompat
  private lateinit var bookmarkStatement: TextView
  private lateinit var buttonNewView: Button

  //inherited elements
  private lateinit var toolbar: PalaceToolbar


  companion object {

    private const val PARAMETERS_ID =
      "fi.kansalliskirjasto.ekirjasto.ui.accounts.ekirjasto.AccountFragment.parameters"

    /**
     * Create a new account fragment for the given parameters.
     */
    fun create(parameters: AccountFragmentParameters): EKirjastoAccountFragment {
      val fragment = EKirjastoAccountFragment()
      fragment.arguments = bundleOf(this.PARAMETERS_ID to parameters)
      return fragment
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    this.buttonLogout = view.findViewById(R.id.buttonLogout)
    this.buttonLoginSuomiFi = view.findViewById(R.id.buttonLoginSuomiFi)
    this.buttonLoginPasskey = view.findViewById(R.id.buttonLoginPasskey)
    this.buttonRegisterPasskey = view.findViewById(R.id.buttonRegisterPasskey)
    this.eulaStatement = view.findViewById(R.id.eulaStatement)
    this.buttonAccessibilityStatement = view.findViewById(R.id.accessibilityStatement)
    this.syncBookmarks = view.findViewById(R.id.accountSyncBookmarks)
    this.bookmarkStatement = view.findViewById(R.id.accountSyncBookmarksStatement)
    this.buttonFeedback = view.findViewById(R.id.buttonFeedback)
    this.buttonPrivacyPolicy = view.findViewById(R.id.buttonPrivacyPolicy)
    this.buttonUserAgreement = view.findViewById(R.id.buttonUserAgreement)
    this.buttonLicenses = view.findViewById(R.id.buttonLicenses)
    this.buttonFaq = view.findViewById(R.id.buttonFaq)
    this.versionText = view.findViewById(R.id.appVersion)
    this.bookmarkSyncCheck = view.findViewById(R.id.accountSyncBookmarksCheck)
    this.buttonNewView = view.findViewById(R.id.buttonNewView)


    this.toolbar =
      view.rootView.findViewWithTag(PalaceToolbar.palaceToolbarName)
    this.bookmarkSyncProgress =
      view.findViewById(R.id.accountSyncProgress)

    this.viewModel.accountLive.observe(this.viewLifecycleOwner) {
      this.reconfigureAccountUI()
    }

    this.viewModel.accountSyncingSwitchStatus.observe(this.viewLifecycleOwner){ status ->
      this.reconfigureBookmarkSyncingSwitch(status)
    }

    this.reconfigureAccountUI()
  }

  override fun onStart() {
    super.onStart()
    this.configureToolbar()
    this.buttonLogout.setOnClickListener(){
      this.logger.debug("Logout clicked")
      this.viewModel.tryLogout()
    }
    this.buttonLoginSuomiFi.setOnClickListener {
      this.logger.debug("Login with Suomi.Fi clicked")
      onTryLoginSuomiFi()
    }
    this.buttonLoginPasskey.setOnClickListener {
      this.logger.debug("Login with Passkey clicked")
      onTryLoginPasskey()
    }
    this.buttonRegisterPasskey.setOnClickListener {
      this.logger.debug("Register Passkey clicked")
      onTryRegisterPasskey()
    }


    /*
     * Configure the bookmark syncing switch to enable/disable syncing permissions.
     */

    this.bookmarkSyncCheck.setOnCheckedChangeListener { _, isChecked ->
      this.viewModel.enableBookmarkSyncing(isChecked)
    }

    /*
     * Hide the toolbar and back arrow if there is no page to return to (e.g. coming from a deep link).
     */
    if (this.parameters.hideToolbar) {
      this.toolbar.visibility = View.GONE
    } else {
      this.toolbar.visibility = View.VISIBLE
    }

    /*
     * Eagerly reconfigure the UI to ensure an up-to-date view when resuming from sleep.
     */

    this.reconfigureAccountUI()
  }

  private fun configureDocViewButton(button: Button, document: DocumentType?){
    button.isEnabled = document != null
    if (document != null) {
      button.setOnClickListener {
        val title = button.text
        val url = LanguageUtil.insertLanguageInURL(document.readableURL)
        logger.debug("OpenDocViewer: {} -> {}", title, url)
        this.listener.post(
          AccountDetailEvent.OpenDocViewer(
            title = title.toString(),
            url = LanguageUtil.insertLanguageInURL(document.readableURL)
          )
        )
      }
    } else {
      this.logger.warn("{} document not found!", button.text.toString())
    }
  }

  private fun configureNewViewButton(button: Button) {
    button.setOnClickListener {
      logger.debug("New view clicked")
      this.listener.post(
        AccountDetailEvent.OpenNewView
      )
    }
  }

  private fun configureToolbar() {
    val providerName = this.viewModel.account.provider.displayName
    val actionBar = this.supportActionBar ?: return
    actionBar.show()
    actionBar.setDisplayHomeAsUpEnabled(true)
    actionBar.setHomeActionContentDescription(null)
    actionBar.setTitle(R.string.AccountTitle)
    this.toolbar.logo
    this.toolbar.setLogoOnClickListener {
      this.listener.post(AccountDetailEvent.GoUpwards)
    }
  }

  private fun reconfigureAccountUI() {

    if (!isPasskeySupported()) {
      this.buttonRegisterPasskey.visibility = GONE
      this.buttonLoginPasskey.visibility = GONE
    }
    val loginState = this.viewModel.account.loginState
    this.logger.debug("Configure UI Account Login State: {}",loginState)
    when (loginState){
      is AccountNotLoggedIn -> OnConfigureNotLoggedIn(loginState)
      is AccountLoggingIn -> OnConfigureAccountLoggingIn(loginState)
      is AccountLoggingInWaitingForExternalAuthentication -> OnConfigureWaitingForExternalAuth(loginState)
      is AccountLoginFailed -> OnConfigureAccountLoginFailed(loginState)
      is AccountLoggedIn -> OnConfigureAccountLoggedIn(loginState)
      is AccountLoginState.AccountLoggingOut -> OnConfigureAccountLoggingOut(loginState)
      is AccountLoginState.AccountLogoutFailed -> OnConfigureAccountLogoutFailed(loginState)
    }

    configureDocViewButton(buttonFeedback, this.viewModel.documents.feedback)
    configureDocViewButton(buttonAccessibilityStatement, this.viewModel.documents.accessibilityStatement)
    configureDocViewButton(buttonPrivacyPolicy, this.viewModel.documents.privacyPolicy)
    configureDocViewButton(buttonUserAgreement, this.viewModel.documents.eula)
    configureDocViewButton(buttonLicenses, this.viewModel.documents.licenses)
    configureDocViewButton(buttonFaq, this.viewModel.documents.faq)

    configureNewViewButton(buttonNewView)

    val versionString = this.requireContext().getString(R.string.app_version_string, this.viewModel.appVersion)
    this.versionText.text = versionString
  }

  private fun OnConfigureNotLoggedIn(loginState: AccountNotLoggedIn) {
    this.buttonLoginPasskey.isEnabled = true
    this.buttonLoginSuomiFi.isEnabled = true
    buttonLogout.visibility = GONE
    buttonLoginSuomiFi.visibility = VISIBLE

    if (isPasskeySupported()) {
      buttonLoginPasskey.visibility = VISIBLE
      buttonRegisterPasskey.visibility = GONE
    }
    this.syncBookmarks.visibility = GONE
    this.bookmarkStatement.visibility = GONE
    this.eulaStatement.visibility = VISIBLE
  }

  private fun OnConfigureAccountLoggingIn(loginState: AccountLoggingIn) {
    this.buttonLoginSuomiFi.isEnabled = false
    this.buttonLoginPasskey.isEnabled = false
    buttonLogout.visibility = GONE
    buttonLoginSuomiFi.visibility = VISIBLE

    if (isPasskeySupported()) {
      buttonLoginPasskey.visibility = VISIBLE
      buttonRegisterPasskey.visibility = GONE
    }
  }

  private fun OnConfigureWaitingForExternalAuth(loginState: AccountLoggingInWaitingForExternalAuthentication) {
    this.buttonLoginSuomiFi.isEnabled = false
    this.buttonLoginPasskey.isEnabled = false
    buttonLogout.visibility = GONE
    buttonLoginSuomiFi.visibility = VISIBLE

    if (isPasskeySupported()) {
      buttonLoginPasskey.visibility = VISIBLE
      buttonRegisterPasskey.visibility = GONE
    }
  }

  private fun OnConfigureAccountLoginFailed(loginState: AccountLoginFailed) {
    this.buttonLoginSuomiFi.isEnabled = true
    this.buttonLoginPasskey.isEnabled = true
    buttonLogout.visibility = GONE
    buttonLoginSuomiFi.visibility = VISIBLE

    if (isPasskeySupported()) {
      buttonLoginPasskey.visibility = VISIBLE
      buttonRegisterPasskey.visibility = GONE
    }
    this.syncBookmarks.visibility = GONE

  }

  private fun OnConfigureAccountLoggedIn(loginState: AccountLoggedIn) {
    buttonLogout.visibility = VISIBLE
    buttonLoginSuomiFi.visibility = GONE
    if (isPasskeySupported()) {
      buttonLoginPasskey.visibility = GONE
      buttonRegisterPasskey.visibility = VISIBLE
    }
    this.syncBookmarks.visibility = VISIBLE
    this.bookmarkStatement.visibility = VISIBLE
    this.bookmarkSyncProgress.visibility = INVISIBLE
    this.eulaStatement.visibility = GONE
  }

  private fun OnConfigureAccountLoggingOut(loginState: AccountLoginState.AccountLoggingOut) {
    buttonLogout.visibility = GONE
    buttonLoginSuomiFi.visibility = GONE
    buttonLoginPasskey.visibility = GONE
    buttonRegisterPasskey.visibility = GONE
  }

  private fun OnConfigureAccountLogoutFailed(loginState: AccountLoginState.AccountLogoutFailed) {
    buttonLogout.visibility = VISIBLE
    buttonLoginSuomiFi.visibility = GONE

    if (isPasskeySupported()) {
      buttonLoginPasskey.visibility = GONE
      buttonRegisterPasskey.visibility = VISIBLE
    }
  }

  private fun onTryRegisterPasskey() {
    val description = this.viewModel.authenticationDescription
    val credentials = this.viewModel.account.loginState.credentials
    val token = credentials?.let {
      if (it is AccountAuthenticationCredentials.Ekirjasto) {
        it.accessToken
      } else {
        null
      }
    }
//    this.viewModel.tryLogin(
//      ProfileAccountLoginRequest.EkirjastoInitiatePassKey(
//        accountId = this.parameters.accountID,
//        description = description
//      )
//    )
    this.listener.post(
      AccountDetailEvent.OpenEkirjastoPasskeyLogin(
        this.parameters.accountID,
        description,
        EkirjastoLoginMethod.Passkey(
          loginState = EkirjastoLoginMethod.Passkey.LoginState.RegisterAvailable,
          circulationToken = token,
        )
      )
    )
  }

  private fun onTryLoginPasskey(
  ) {

    val description = this.viewModel.authenticationDescription
    this.logger.debug("TryEkirjastoPasskeyLogin")

    this.viewModel.tryLogin(
      ProfileAccountLoginRequest.EkirjastoInitiatePassKey(
      accountId = this.parameters.accountID,
      description = description
    ))
    this.listener.post(
      AccountDetailEvent.OpenEkirjastoPasskeyLogin(
        this.parameters.accountID,
        description,
        EkirjastoLoginMethod.Passkey(
          loginState = EkirjastoLoginMethod.Passkey.LoginState.LoggingIn,
          circulationToken = null,
        )
      )
    )

  }
  // Finland
  private fun onTryLoginSuomiFi(
  ) {
    val authenticationDescription = this.viewModel.authenticationDescription
    this.viewModel.tryLogin(
      ProfileAccountLoginRequest.EkirjastoInitiateSuomiFi(
        accountId = this.parameters.accountID,
        description = authenticationDescription
      )
    )
    this.listener.post(
      AccountDetailEvent.OpenEkirjastoSuomiFiLogin(this.parameters.accountID, authenticationDescription, EkirjastoLoginMethod.SuomiFi())
    )
  }

  override fun onStop() {
    super.onStop()

    /*
     * Broadcast the login state. The reason for doing this is that consumers might be subscribed
     * to the account so that they can perform actions when the user has either attempted to log
     * in, or has cancelled without attempting it. The consumers have no way to detect the fact
     * that the user didn't even try to log in unless we tell the account to broadcast its current
     * state.
     */

    this.logger.debug("broadcasting login state")
    this.viewModel.account.setLoginState(this.viewModel.account.loginState)

    this.subscriptions.clear()
  }

  private fun isPasskeySupported(): Boolean {
    return android.os.Build.VERSION.SDK_INT >= 28
  }

  private fun reconfigureBookmarkSyncingSwitch(status: BookmarkSyncEnableStatus) {
    /*
     * Remove the checked-change listener, because setting `isChecked` will trigger the listener.
     */

    this.bookmarkSyncCheck.setOnCheckedChangeListener(null)

    /*
     * Otherwise, the switch is doing something that interests us...
     */

    val account = this.viewModel.account
    return when (status) {
      is BookmarkSyncEnableStatus.Changing -> {
        this.bookmarkSyncProgress.visibility = View.VISIBLE
        this.bookmarkSyncCheck.isEnabled = false
      }

      is BookmarkSyncEnableStatus.Idle -> {
        this.bookmarkSyncProgress.visibility = View.INVISIBLE
        this.logger.debug("Bookmark Syncing Status: $status")
        when (status.status) {

          BookmarkSyncEnableResult.SYNC_ENABLE_NOT_SUPPORTED -> {
            this.bookmarkSyncCheck.isChecked = false
            this.bookmarkSyncCheck.isEnabled = false
          }

          BookmarkSyncEnableResult.SYNC_ENABLED,
          BookmarkSyncEnableResult.SYNC_DISABLED -> {
            val isPermitted = account.preferences.bookmarkSyncingPermitted
            val isSupported = account.loginState.credentials?.annotationsURI != null

            this.bookmarkSyncCheck.isChecked = isPermitted
            this.bookmarkSyncCheck.isEnabled = isSupported

            this.bookmarkSyncCheck.setOnCheckedChangeListener { _, isChecked ->
              this.viewModel.enableBookmarkSyncing(isChecked)
            }
          }
        }
      }
    }
  }

}
