package org.nypl.simplified.ui.accounts.ekirjasto

import android.os.Bundle
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.Button
import android.widget.ProgressBar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import io.reactivex.disposables.CompositeDisposable
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
        listener = this.listener
      )
    }
  )

  //elements
  private lateinit var buttonLogout: Button

  private lateinit var buttonLoginSuomiFi: Button

  private lateinit var buttonLoginPasskey: Button
  private lateinit var buttonRegisterPasskey: Button
  //inherited elements
  private lateinit var toolbar: PalaceToolbar
  private lateinit var bookmarkSyncProgress: ProgressBar

  private lateinit var syncBookmarks: ConstraintLayout


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
    this.syncBookmarks = view.findViewById(R.id.accountSyncBookmarks)

    this.toolbar =
      view.rootView.findViewWithTag(PalaceToolbar.palaceToolbarName)
    this.bookmarkSyncProgress =
      view.findViewById(R.id.accountSyncProgress)

    this.viewModel.accountLive.observe(this.viewLifecycleOwner) {
      this.reconfigureAccountUI()
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
// TODO bookmark sync
//    this.bookmarkSyncCheck.setOnCheckedChangeListener { _, isChecked ->
//      this.viewModel.enableBookmarkSyncing(isChecked)
//    }

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
  }

  private fun OnConfigureNotLoggedIn(loginState: AccountNotLoggedIn) {
    this.buttonLoginPasskey.isEnabled = true
    this.buttonLoginSuomiFi.isEnabled = true
    buttonLogout.visibility = GONE
    buttonLoginSuomiFi.visibility = VISIBLE
    buttonLoginPasskey.visibility = VISIBLE
    buttonRegisterPasskey.visibility = GONE
    this.syncBookmarks.visibility = GONE
  }

  private fun OnConfigureAccountLoggingIn(loginState: AccountLoggingIn) {
    this.buttonLoginSuomiFi.isEnabled = false
    this.buttonLoginPasskey.isEnabled = false
    buttonLogout.visibility = GONE
    buttonLoginSuomiFi.visibility = VISIBLE
    buttonLoginPasskey.visibility = VISIBLE
    buttonRegisterPasskey.visibility = GONE
  }

  private fun OnConfigureWaitingForExternalAuth(loginState: AccountLoggingInWaitingForExternalAuthentication) {
    this.buttonLoginSuomiFi.isEnabled = false
    this.buttonLoginPasskey.isEnabled = false
    buttonLogout.visibility = GONE
    buttonLoginSuomiFi.visibility = VISIBLE
    buttonLoginPasskey.visibility = VISIBLE
    buttonRegisterPasskey.visibility = GONE
  }

  private fun OnConfigureAccountLoginFailed(loginState: AccountLoginFailed) {
    this.buttonLoginSuomiFi.isEnabled = true
    this.buttonLoginPasskey.isEnabled = true
    buttonLogout.visibility = GONE
    buttonLoginSuomiFi.visibility = VISIBLE
    buttonLoginPasskey.visibility = VISIBLE
    buttonRegisterPasskey.visibility = GONE

  }

  private fun OnConfigureAccountLoggedIn(loginState: AccountLoggedIn) {
    buttonLogout.visibility = VISIBLE
    buttonLoginSuomiFi.visibility = GONE
    buttonLoginPasskey.visibility = GONE
    buttonRegisterPasskey.visibility = VISIBLE
    this.syncBookmarks.visibility = VISIBLE
    this.bookmarkSyncProgress.visibility = INVISIBLE
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
    buttonLoginPasskey.visibility = GONE
    buttonRegisterPasskey.visibility = VISIBLE
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
    this.viewModel.tryLogin(
      ProfileAccountLoginRequest.EkirjastoInitiatePassKey(
        accountId = this.parameters.accountID,
        description = description
      )
    )
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

}
