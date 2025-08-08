package org.nypl.simplified.ui.accounts.ekirjasto.passkey

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.credentials.CredentialManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

    fun create(parameters: AccountEkirjastoPasskeyFragmentParameters): AccountEkirjastoPasskeyFragment {
      val fragment =
        AccountEkirjastoPasskeyFragment()
      fragment.arguments =
        bundleOf(PARAMETERS_ID to parameters)
      return fragment
    }
  }

  private val parameters: AccountEkirjastoPasskeyFragmentParameters by lazy {
    this.requireArguments()[PARAMETERS_ID] as AccountEkirjastoPasskeyFragmentParameters
  }

  private val logger =
    LoggerFactory.getLogger(AccountEkirjastoPasskeyFragment::class.java)

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
        application = this.requireActivity(),
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
    viewModel.passkeyResult.observe(viewLifecycleOwner){
      when (it) {
        is TaskResult.Success<PasskeyAuth> -> if (this.viewModel.isRegistering){
          listener.post(AccountEkirjastoSuomiFiEvent.PasskeySuccessful)
        } else {
          postPasskeySuccessful(it.result)
        }
        is TaskResult.Failure<PasskeyAuth> ->
          //If failure was caused by user canceling the passkey login
          //We only want to cancel the login try without showing error message
          if (this.viewModel.isCancelled) {
            listener.post(AccountEkirjastoSuomiFiEvent.Cancel)
          } else {
            postPasskeyFailed(it)
          }
      }
    }
  }

  private fun postPasskeySuccessful(authInfo: PasskeyAuth) {
    this.logger.debug("Passkey Login Successful")
    this.profilesController.profileAccountLogin(
      ProfileAccountLoginRequest.EkirjastoComplete(
        accountId = this.parameters.accountID,
        description = this.parameters.authenticationDescription,
        ekirjastoToken = authInfo.token,
      )
    )
    this.listener.post(AccountEkirjastoSuomiFiEvent.PasskeySuccessful)
  }

  private fun postPasskeyFailed(result: TaskResult.Failure<PasskeyAuth>) {
    //If error in registering, handle special case of it being user cancelled
    if (this.viewModel.isRegistering){
      //Check if request was cancelled by the user
      //If yes, don't show error message, and just toast informing of cancel
      if (result.steps.last().message == "User cancelled request") {
        Toast.makeText(this.requireContext(), R.string.errorPasskeyRegisterCancelled, Toast.LENGTH_SHORT).show()
        //Post to return back to settings view
        this.listener.post(AccountEkirjastoSuomiFiEvent.Cancel)
        return
      }
    }
    val msg = if(this.viewModel.isRegistering) {R.string.errorPasskeyRegisterFailed} else {R.string.errorPasskeyLoginFailed}
    val newDialog =
      MaterialAlertDialogBuilder(this.requireActivity())
        .setTitle(R.string.errorLoginFailed)
        .setMessage(msg)
        .setPositiveButton(R.string.accountsDetails) { dialog, _ ->
          this.showErrorPage(result.steps)
          dialog.dismiss()
        }
        .setOnCancelListener{
          this.listener.post(AccountEkirjastoSuomiFiEvent.Cancel)
        }
          .create()
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
        taskSteps = taskSteps,
        popPrevious = true
      )

    this.listener.post(AccountEkirjastoSuomiFiEvent.OpenErrorPage(parameters))
  }

  override fun onStart() {
    super.onStart()
    logger.debug("$tag Passkey Fragment OnStart")

    val loginState = parameters.loginMethod.loginState

    logger.debug("loginstate: {}", loginState)

    //todo: register events

    when (loginState) {
      EkirjastoLoginMethod.Passkey.LoginState.RegisterAvailable -> passkeyRegisterAsync()
      EkirjastoLoginMethod.Passkey.LoginState.LoggingIn -> passkeyLoginAsync()
      else -> this.logger.warn("Unhandled login state: {}", loginState)
    }
  }

  private fun passkeyLoginAsync() {
    this.logger.debug("Passkey Login Async")

    this.viewModel.passkeyLogin()

//    lifecycleScope.launch {
//      val result = viewModel.passkeyLogin()
//      when (result) {
//        is TaskResult.Success<PasskeyAuth> -> postPasskeySuccessful(result.result)
//        is TaskResult.Failure<PasskeyAuth> -> postPasskeyFailed(result)
//      }
//    }
  }

  private fun passkeyRegisterAsync() {
    logger.debug("Passkey Register Async")
    if (parameters.loginMethod.circulationToken == null) {
      //handleFailure(Exception("Missing token. Cannot complete passkey registration"))
      return
    }
    viewModel.passkeyRegister()
//    lifecycleScope.launch {
//      val result = viewModel.passkeyRegister()
//      when (result) {
//        is TaskResult.Success<PasskeyAuth> -> listener.post(AccountEkirjastoSuomiFiEvent.PasskeySuccessful)
//        is TaskResult.Failure<PasskeyAuth> -> postPasskeyFailed(result)
//      }
//    }
  }

  override fun onStop() {
    super.onStop()
  }
}
