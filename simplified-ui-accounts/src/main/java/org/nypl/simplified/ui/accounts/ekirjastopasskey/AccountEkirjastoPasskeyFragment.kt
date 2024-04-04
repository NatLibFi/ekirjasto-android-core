package org.nypl.simplified.ui.accounts.ekirjastopasskey

import androidx.core.os.bundleOf
import androidx.credentials.CredentialManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.launch
import java.net.URI
import java.nio.charset.Charset
import one.irradia.mime.api.MIMEType
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPRequestBuilderType.Method.Post
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.accounts.R
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.listeners.api.fragmentListeners
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskStep
import org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels.PasskeyAuth
import org.nypl.simplified.ui.accounts.ekirjastosuomifi.AccountEkirjastoSuomiFiEvent
import org.nypl.simplified.ui.accounts.ekirjastosuomifi.AccountEkirjastoSuomiFiInternalEvent
import org.nypl.simplified.ui.accounts.ekirjastosuomifi.EkirjastoLoginMethod
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
      val fragment = AccountEkirjastoPasskeyFragment()
      fragment.arguments = bundleOf(this.PARAMETERS_ID to parameters)
      return fragment
    }
  }

  private val parameters: AccountEkirjastoPasskeyFragmentParameters by lazy {
    this.requireArguments()[PARAMETERS_ID] as AccountEkirjastoPasskeyFragmentParameters
  }

  private val logger = LoggerFactory.getLogger(AccountEkirjastoPasskeyFragment::class.java)

  private val listener: FragmentListenerType<AccountEkirjastoSuomiFiEvent> by fragmentListeners()

  private val services = Services.serviceDirectory()

  private val buildConfig = services.requireService(BuildConfigurationServiceType::class.java)

  private val profilesController = services.requireService(ProfilesControllerType::class.java)

  //todo remove
  private val http = this.services.requireService(LSHTTPClientType::class.java)

  private val tag = "PASSKEY";

  val supportEmailAddress: String = buildConfig.supportErrorReportEmailAddress


  private lateinit var credentialManager: CredentialManager;// = CredentialManager.create(requireContext())

  private lateinit var progress: ProgressBar

  private val viewModel: AccountEkirjastoPasskeyViewModel by viewModels(
    factoryProducer = {
      AccountEkirjastoPasskeyViewModelFactory(
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

  private fun passkeyLoginAsync(username: String) {
    this.logger.debug("Passkey Login Async")

    lifecycleScope.launch {
      val auth: PasskeyAuth = viewModel.passkeyLogin(username)
      if (auth.success) {
        postPasskeySuccessful(auth)
      } else {
        postPasskeyFailed(Exception("Passkey authenticate failed"))
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
        username = this.parameters.loginMethod.username?.value
      )
    )
    this.listener.post(AccountEkirjastoSuomiFiEvent.PasskeySuccessful)
  }

  private fun postPasskeyFailed(exception: Throwable) {
    val newDialog =
      AlertDialog.Builder(this.requireActivity())
        .setTitle(R.string.accountCreationFailed)
        .setMessage(R.string.accountCreationFailedMessage)
        .setPositiveButton(R.string.accountsDetails) { dialog, _ ->
          this.showErrorPage(this.makeLoginTaskSteps(exception.message?:"Passkey login failed"))
          dialog.dismiss()
        }.create()
    newDialog.show()
  }

  private fun makeLoginTaskSteps(
    message: String
  ): List<TaskStep> {
    val taskRecorder = TaskRecorder.create()
    taskRecorder.beginNewStep("Started E-kirjasto login...")
    taskRecorder.currentStepFailed(message, "suomifiAccountCreationFailed")
    return taskRecorder.finishFailure<AccountType>().steps
  }

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

  private fun passkeyRegisterAsync(username: String) {
    logger.debug("Passkey Register Async")
    if (parameters.loginMethod.circulationToken == null) {
      //handleFailure(Exception("Missing token. Cannot complete passkey registration"))
      return
    }
    lifecycleScope.launch {
      val auth = viewModel.passkeyRegister(username)
      if (auth.success) {
        listener.post(AccountEkirjastoSuomiFiEvent.PasskeySuccessful)
      } else {
        postPasskeyFailed(Exception("Passkey Registration Failed"))
      }
    }
  }

  override fun onStop() {
    super.onStop()
  }
}
