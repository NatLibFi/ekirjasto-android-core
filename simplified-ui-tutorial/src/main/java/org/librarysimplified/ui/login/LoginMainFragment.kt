package org.librarysimplified.ui.login

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.ViewModelProvider
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.tutorial.R
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.listeners.api.ListenerRepository
import org.nypl.simplified.listeners.api.fragmentListeners
import org.nypl.simplified.listeners.api.listenerRepositories
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.accounts.ekirjasto.passkey.AccountEkirjastoPasskeyFragment
import org.nypl.simplified.ui.accounts.ekirjasto.passkey.AccountEkirjastoPasskeyFragmentParameters
import org.nypl.simplified.ui.accounts.ekirjasto.suomifi.AccountEkirjastoSuomiFiEvent
import org.nypl.simplified.ui.accounts.ekirjasto.suomifi.AccountEkirjastoSuomiFiFragment
import org.nypl.simplified.ui.accounts.ekirjasto.suomifi.AccountEkirjastoSuomiFiFragmentParameters
import org.nypl.simplified.ui.accounts.ekirjasto.suomifi.EkirjastoLoginMethod
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.slf4j.LoggerFactory


class LoginMainFragment : Fragment(R.layout.login_main_fragment) {
  private val logger = LoggerFactory.getLogger(LoginMainFragment::class.java)
  private lateinit var mainContainer: FrameLayout
  //TODO login events listener
  private val listenerRepository: ListenerRepository<LoginListenedEvent, Unit> by listenerRepositories()
  private val listener: FragmentListenerType<MainLoginEvent> by fragmentListeners()
  private val defaultViewModelFactory: ViewModelProvider.Factory by lazy {
    LoginMainFragmentViewModelFactory(super.defaultViewModelProviderFactory)
  }
  override val defaultViewModelProviderFactory: ViewModelProvider.Factory
    get() = this.defaultViewModelFactory

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    mainContainer = view.findViewById(R.id.login_main_container)

  }
  override fun onStart() {
    super.onStart()
    this.listenerRepository.registerHandler(this::handleEvent)
    showLoginUi();
  }

  override fun onStop() {
    super.onStop()
    this.listenerRepository.unregisterHandler()
  }

  private fun showLoginUi() {
    //TODO should I do something about backstack
    childFragmentManager.commit {
      replace(R.id.login_main_container, LoginUiFragment())
    }
  }

  private fun handleEvent(event: LoginListenedEvent, unit: Unit) {
    this.logger.debug("Handle Login Event: $event")
    when (event){
      is LoginListenedEvent.LoginEvent -> {
        when (event.event){
          is LoginEvent.SkipLogin -> this.listener.post(MainLoginEvent.SkipLoginEvent)
          is LoginEvent.StartLoginPasskey -> openEkirjastoLogin(EkirjastoLoginMethod.Passkey(EkirjastoLoginMethod.Passkey.LoginState.LoggingIn, null))
          is LoginEvent.StartLoginSuomiFi -> openEkirjastoLogin(EkirjastoLoginMethod.SuomiFi())
        }
      }
      is LoginListenedEvent.AccountDetailEvent -> {
        this.logger.warn("Received Account Detail Event: ${event.event}")
      }
      is LoginListenedEvent.SuomiFiEvent -> {
        this.logger.warn("Received Suomi.Fi event: ${event.event}")
        when(val suomiFiEvent = event.event) {
          is AccountEkirjastoSuomiFiEvent.PasskeySuccessful,
          is AccountEkirjastoSuomiFiEvent.AccessTokenObtained -> this.listener.post(MainLoginEvent.LoginSuccess)
          is AccountEkirjastoSuomiFiEvent.OpenErrorPage -> openErrorPage(suomiFiEvent.parameters)
        }
      }
    }
  }

  private fun openErrorPage(parameters: ErrorPageParameters) {
    TODO("Not yet implemented")
  }

  private fun pickDefaultAccount(
    profilesController: ProfilesControllerType,
    defaultProvider: AccountProviderType
  ): AccountType {

    val profile = profilesController.profileCurrent()
    val mostRecentId = profile.preferences().mostRecentAccount
    if (mostRecentId != null) {
      try {
        return profile.account(mostRecentId)
      } catch (e: Exception) {
        this.logger.error("stale account: ", e)
      }
    }

    val accounts = profile.accounts().values
    return when {
      accounts.size > 1 -> {
        // Return the first account created from a non-default provider
        accounts.first { it.provider.id != defaultProvider.id }
      }
      accounts.size == 1 -> {
        // Return the first account
        accounts.first()
      }
      else -> {
        // There should always be at least one account
        throw Exception("Unreachable code")
      }
    }
  }

  private fun openEkirjastoLogin(method: EkirjastoLoginMethod) {
    val services =
      Services.serviceDirectory()
    val profilesController =
      services.requireService(ProfilesControllerType::class.java)
    val accountProviders =
      services.requireService(AccountProviderRegistryType::class.java)

    this.logger.warn("Current Accounts: "+profilesController.profileCurrent().accounts().count())
    this.logger.warn("Current Providers: "+accountProviders.resolvedProviders.count())
    val account = pickDefaultAccount(profilesController, accountProviders.defaultProvider)
    val authentication = account.provider.authentication as AccountProviderAuthenticationDescription.Ekirjasto

    when (method) {
      is EkirjastoLoginMethod.SuomiFi -> openSuomiFiLogin(
        profilesController,
        account.id,
        authentication
      )

      is EkirjastoLoginMethod.Passkey -> openPasskeyLogin(
        profilesController,
        account.id,
        authentication
      )
    }
  }

  private fun openSuomiFiLogin(
    profilesController: ProfilesControllerType,
    accountId: AccountID,
    description: AccountProviderAuthenticationDescription.Ekirjasto
  ) {
    this.logger.debug("From Login Screen open Suomi.Fi login")


    profilesController.profileAccountLogin(
      ProfileAccountLoginRequest.EkirjastoInitiateSuomiFi(
        accountId = accountId,
        description = description
      )
    )

    val fragment = AccountEkirjastoSuomiFiFragment.create(
        AccountEkirjastoSuomiFiFragmentParameters(
          accountId,
          description
        )
      )

    this.childFragmentManager.commit {
      replace(R.id.login_main_container, fragment)
    }

  }

  private fun openPasskeyLogin(
    profilesController: ProfilesControllerType,
    accountId: AccountID,
    description: AccountProviderAuthenticationDescription.Ekirjasto
  ) {
    this.logger.debug("From Login Screen open Passkey login")

    profilesController.profileAccountLogin(
      ProfileAccountLoginRequest.EkirjastoInitiatePassKey(
        accountId = accountId,
        description = description
      )
    )

    val fragment = AccountEkirjastoPasskeyFragment.create(
        AccountEkirjastoPasskeyFragmentParameters(
          accountId,
          description,
          EkirjastoLoginMethod.Passkey(EkirjastoLoginMethod.Passkey.LoginState.LoggingIn, null)
        )
      )
    this.childFragmentManager.commit {
      replace(R.id.login_main_container, fragment)
    }

  }
}
