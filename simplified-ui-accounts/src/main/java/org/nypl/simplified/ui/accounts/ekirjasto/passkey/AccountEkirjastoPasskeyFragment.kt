package org.nypl.simplified.ui.accounts.ekirjasto.passkey

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.credentials.CredentialManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.accounts.R
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.listeners.api.fragmentListeners
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.taskrecorder.api.TaskStep
import org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.PasskeyAuth
import org.nypl.simplified.ui.accounts.ekirjasto.suomifi.AccountEkirjastoSuomiFiEvent
import org.nypl.simplified.ui.accounts.ekirjasto.suomifi.EkirjastoLoginMethod
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.slf4j.LoggerFactory

/**
 * A fragment that performs the E-kirjasto passkey login workflow.
 */

class AccountEkirjastoPasskeyFragment : Fragment(R.layout.account_ekirjastopasskey) {

  companion object {

    private const val PARAMETERS_ID =
      "org.nypl.simplified.ui.accounts.ekirjastopasskey.AccountEkirjastoPasskeyFragment"

    /**
     * Create a new account fragment for the given parameters.
     */

    fun create(parameters: org.nypl.simplified.ui.accounts.ekirjasto.passkey.AccountEkirjastoPasskeyFragmentParameters): org.nypl.simplified.ui.accounts.ekirjasto.passkey.AccountEkirjastoPasskeyFragment {
      val fragment =
        org.nypl.simplified.ui.accounts.ekirjasto.passkey.AccountEkirjastoPasskeyFragment()
      fragment.arguments = bundleOf(org.nypl.simplified.ui.accounts.ekirjasto.passkey.AccountEkirjastoPasskeyFragment.Companion.PARAMETERS_ID to parameters)
      return fragment
    }
  }

  private val parameters: org.nypl.simplified.ui.accounts.ekirjasto.passkey.AccountEkirjastoPasskeyFragmentParameters by lazy {
    this.requireArguments()[org.nypl.simplified.ui.accounts.ekirjasto.passkey.AccountEkirjastoPasskeyFragment.Companion.PARAMETERS_ID] as org.nypl.simplified.ui.accounts.ekirjasto.passkey.AccountEkirjastoPasskeyFragmentParameters
  }

  private val logger = LoggerFactory.getLogger(org.nypl.simplified.ui.accounts.ekirjasto.passkey.AccountEkirjastoPasskeyFragment::class.java)

  private val listener: FragmentListenerType<AccountEkirjastoSuomiFiEvent> by fragmentListeners()

  private val services = Services.serviceDirectory()

  private val buildConfig = services.requireService(BuildConfigurationServiceType::class.java)

  private val profilesController = services.requireService(ProfilesControllerType::class.java)

  private val tag = "PASSKEY"

  val supportEmailAddress: String = buildConfig.supportErrorReportEmailAddress


  private lateinit var credentialManager: CredentialManager

  private lateinit var progress: ProgressBar

  private val viewModel: org.nypl.simplified.ui.accounts.ekirjasto.passkey.AccountEkirjastoPasskeyViewModel by viewModels(
    factoryProducer = {
      org.nypl.simplified.ui.accounts.ekirjasto.passkey.AccountEkirjastoPasskeyViewModelFactory(
        application = this.requireActivity().application,
        account = this.parameters.accountID,
        description = this.parameters.authenticationDescription,
        ekirjastoToken = this.parameters.loginMethod.circulationToken
      )
    }
  )

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    this.progress = view.findViewById(R.id.passkeyprogressBar)
    credentialManager = CredentialManager.create(requireContext())
  }

  private fun postPasskeySuccessful(authInfo: PasskeyAuth) {
    this.logger.debug("Passkey Login Successful")
    this.profilesController.profileAccountLogin(
      ProfileAccountLoginRequest.EkirjastoComplete(
        accountId = this.parameters.accountID,
        description = this.parameters.authenticationDescription,
        ekirjastoToken = authInfo.token,
        username = this.parameters.loginMethod.username?.value
      )
    )
    this.listener.post(AccountEkirjastoSuomiFiEvent.PasskeySuccessful)
  }

  private fun postPasskeyFailed(result: TaskResult.Failure<PasskeyAuth>) {
    val newDialog =
      AlertDialog.Builder(this.requireActivity())
        .setTitle(R.string.accountCreationFailed)
        .setMessage(R.string.accountCreationFailedMessage)
        .setPositiveButton(R.string.accountsDetails) { dialog, _ ->
          this.showErrorPage(result.steps)
          dialog.dismiss()
        }.create()
    newDialog.show()
  }

//  private fun makeLoginTaskSteps(

  private fun showErrorPage(taskSteps: List<TaskStep>) {
    val parameters =
      ErrorPageParameters(
        emailAddress = this.viewModel.supportEmailAddress,
        body = "",
        subject = "[ekirjasto-error-report]",
        attributes = sortedMapOf(),
        taskSteps = taskSteps
      )

    this.listener.post(AccountEkirjastoSuomiFiEvent.OpenErrorPage(parameters))
  }

  override fun onStart() {
    super.onStart()
    logger.debug("$tag Passkey Fragment OnStart")

    val loginState = parameters.loginMethod.loginState
    val username = parameters.loginMethod.username!!.value

    logger.debug("loginstate: {}", loginState)

    //todo: register events

    when (loginState) {
      EkirjastoLoginMethod.Passkey.LoginState.RegisterAvailable -> passkeyRegisterAsync(username)
      EkirjastoLoginMethod.Passkey.LoginState.LoggingIn -> passkeyLoginAsync(username)
      else -> this.logger.warn("Unhandled login state: {}", loginState)
    }
  }

  private fun passkeyLoginAsync(username: String) {
    this.logger.debug("Passkey Login Async")

    lifecycleScope.launch {
      val result = viewModel.passkeyLogin(username)
      when (result){
        is TaskResult.Success<PasskeyAuth> -> postPasskeySuccessful(result.result)
        is TaskResult.Failure<PasskeyAuth> -> postPasskeyFailed(result)
      }
    }
  }

  private fun passkeyRegisterAsync(username: String) {
    logger.debug("Passkey Register Async")
    if (parameters.loginMethod.circulationToken == null) {
      //handleFailure(Exception("Missing token. Cannot complete passkey registration"))
      return
    }
    lifecycleScope.launch {
      val result = viewModel.passkeyRegister(username)
      when (result){
        is TaskResult.Success<PasskeyAuth> -> listener.post(AccountEkirjastoSuomiFiEvent.PasskeySuccessful)
        is TaskResult.Failure<PasskeyAuth> -> postPasskeyFailed(result)
      }
    }
  }

  override fun onStop() {
    super.onStop()
  }
}
