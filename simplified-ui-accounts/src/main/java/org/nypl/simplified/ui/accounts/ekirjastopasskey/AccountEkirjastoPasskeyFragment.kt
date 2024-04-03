package org.nypl.simplified.ui.accounts.ekirjastopasskey

import androidx.core.os.bundleOf
import androidx.credentials.CredentialManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
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
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.listeners.api.fragmentListeners
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels.PasskeyAuth
import org.nypl.simplified.ui.accounts.ekirjastosuomifi.AccountEkirjastoSuomiFiEvent
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

  private val viewModel : AccountEkirjastoPasskeyViewModel by viewModels(
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

  // Finland
  private fun getAccessTokenFromEkirjastoAPIResponse(node: JsonNode): String {
    return try {
      node.get("token").asText()
    } catch (e: Exception) {
      this.logger.error("Error getting access token from E-kirjasto API token response: ", e)
      throw e
    }
  }

  private fun passkeyRequest(uri: URI, json: String): LSHTTPResponseStatus.Responded.OK? {
    var isFinishRequest = false
    if (uri == parameters.authenticationDescription.passkey_login_finish
        || uri == parameters.authenticationDescription.passkey_register_finish) {
      isFinishRequest = true
    }
    logger.debug("$tag passkeyRequest ($uri) isFinishRequest: $isFinishRequest")

    val httpRequest = this.http.newRequest(uri)
      .setMethod(Post(
        json.toByteArray(Charset.forName("UTF-8")),
        MIMEType("application", "json", mapOf())
      ))
      .build()

    httpRequest.execute().use { response ->
      when (val status = response.status) {
        is LSHTTPResponseStatus.Responded.OK -> {
          logger.debug("$tag passkeyRequest ($uri) OK: ${status.bodyStream.toString()}")

          // User is registered, registration started or finished successfully.
          return status
        }

        is LSHTTPResponseStatus.Responded.Error -> {

          val statusCode = status.properties?.status ?: 999
          logger.debug("$tag passkeyRequest ($uri) ERROR: ${status.toString()}")
          logger.debug("$tag passkeyRequest ($uri) ERROR: statusCode ${statusCode}")
          val startRequestFailed = (!isFinishRequest && statusCode != 404)
          if (isFinishRequest || startRequestFailed) {
            throw Exception()
          }

          // User was not registered.
          return null
        }

        is LSHTTPResponseStatus.Failed -> {
          throw status.exception
        }
      }
    }
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


//    val requestJson = "";
//
//    val getPublicKeyCredentialOption = GetPublicKeyCredentialOption(
//      requestJson = username
//    )
//    val credentialRequest = GetCredentialRequest(
//      listOf(getPublicKeyCredentialOption)
//    )

//    lifecycleScope.launch {
//      try {
//        val result = credentialManager.getCredential(requireContext(), credentialRequest)
//
//        // Handle the successfully returned credential.
//        when (val credential = result.credential) {
//          is PublicKeyCredential -> {
//            val responseJson = credential.authenticationResponseJson
//            logger.debug("passkey response: $responseJson")
//            /*
//            * something like:  {
//              "id": "KEDetxZcUfinhVi6Za5nZQ",
//              "type": "public-key",
//              "rawId": "KEDetxZcUfinhVi6Za5nZQ",
//              "response": {
//                "clientDataJSON": "eyJ0eXBlIjoid2ViYXV0aG4uZ2V0IiwiY2hhbGxlbmdlIjoiVDF4Q3NueE0yRE5MMktkSzVDTGE2Zk1oRDdPQnFobzZzeXpJbmtfbi1VbyIsIm9yaWdpbiI6ImFuZHJvaWQ6YXBrLWtleS1oYXNoOk1MTHpEdll4UTRFS1R3QzZVNlpWVnJGUXRIOEdjVi0xZDQ0NEZLOUh2YUkiLCJhbmRyb2lkUGFja2FnZU5hbWUiOiJjb20uZ29vZ2xlLmNyZWRlbnRpYWxtYW5hZ2VyLnNhbXBsZSJ9",
//                "authenticatorData": "j5r_fLFhV-qdmGEwiukwD5E_5ama9g0hzXgN8thcFGQdAAAAAA",
//                "signature": "MEUCIQCO1Cm4SA2xiG5FdKDHCJorueiS04wCsqHhiRDbbgITYAIgMKMFirgC2SSFmxrh7z9PzUqr0bK1HZ6Zn8vZVhETnyQ",
//                "userHandle": "2HzoHm_hY0CjuEESY9tY6-3SdjmNHOoNqaPDcZGzsr0"
//              }
//            }
//            * */
//          }
//          else -> {
//            // Catch any unrecognized credential type here
//            logger.error("Unexpected type of credential")
////            handleFailure(Exception("Unexpected type of credential"))
//            return@launch
//          }
//        }
//      } catch (e: NoCredentialException) {
//        logger.error("No credential available", e)
////        handleFailure(e)
//        return@launch
//      } catch (e : GetCredentialException) {
////        handleFailure(e)
//        return@launch
//      }
//
//      // TODO Gather correct json information. Maybe responseJson?
//      var result:LSHTTPResponseStatus.Responded.OK? = null
//      try {
//        result = passkeyRequest(
//          parameters.authenticationDescription.passkey_login_finish,
//          "{\"id\": \"request.id\", \"data\": \"data\"}"
//        )
//      } catch (e : Exception) {
////        handleFailure(e)
//        return@launch
//      }
//
////      finishPasskey(result)
    }
  }



  private fun postPasskeySuccessful(authInfo: PasskeyAuth) {
    this.logger.debug("Passkey Successful: $authInfo")
//    this.profilesController.profileAccountLogin(
//      ProfileAccountLoginRequest.EkirjastoComplete(
//        accountId = this.parameters.accountID,
//        description = this.parameters.authenticationDescription,
//        ekirjastoToken = authInfo.token,
//        username = this.parameters.loginMethod.username?.value
//      )
//    )
    this.listener.post(AccountEkirjastoSuomiFiEvent.PasskeySuccessful)
  }

  private fun postPasskeyFailed(exception: Throwable) {
    // TODO I don't know if this correct at all.
    this.profilesController.profileAccountLogin(
      ProfileAccountLoginRequest.EkirjastoCancel(
        accountId = this.parameters.accountID,
        description = this.parameters.authenticationDescription,
        username = this.parameters.loginMethod.username?.value
      )
    )

    val parameters =
      ErrorPageParameters(
        emailAddress = this.supportEmailAddress,
        body = "",
        subject = "[simplye-error-report]",
        attributes = sortedMapOf(),
        taskSteps = listOf()
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
        postPasskeySuccessful(auth)
      } else {
        postPasskeyFailed(Exception("Passkey Authentication Failed: Got PasskeyAuth.success = false"))
      }
    }
  }

  override fun onStop() {
    super.onStop()
  }
}
