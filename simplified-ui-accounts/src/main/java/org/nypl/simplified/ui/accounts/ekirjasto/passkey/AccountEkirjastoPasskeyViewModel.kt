package org.nypl.simplified.ui.accounts.ekirjasto.passkey

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialCustomException
import androidx.credentials.exceptions.CreateCredentialInterruptedException
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import one.irradia.mime.api.MIMEType
import org.librarysimplified.http.api.LSHTTPAuthorizationBearerToken
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPRequestBuilderType.Method.Post
import org.librarysimplified.http.api.LSHTTPRequestType
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.librarysimplified.http.api.LSHTTPResponseType
import org.librarysimplified.services.api.Services
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.PasskeyAuth
import org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.authenticate.AuthenticateFinishRequest
import org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.authenticate.AuthenticateParameters
import org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.authenticate.AuthenticatePublicKey
import org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.authenticate.AuthenticateResult
import org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.register.FinishRegisterRequest
import org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.register.RegisterParameters
import org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.register.RegisterResult
import org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.register.RegisterSignedChallengeRequest
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URI
import java.nio.charset.Charset

class AccountEkirjastoPasskeyViewModel (
  private val application: Activity,
  private val account: AccountID,
  private val description: AccountProviderAuthenticationDescription.Ekirjasto,
  // Used for register passkey
  private val circulationToken: String?,
  // Used for passkey login
  credentialManager: CredentialManager
) : ViewModel() {

  private val services = Services.serviceDirectory()
  private val profiles = services.requireService(ProfilesControllerType::class.java)
  private val http = this.services.requireService(LSHTTPClientType::class.java)
  private val logger = LoggerFactory.getLogger(AccountEkirjastoPasskeyViewModel::class.java)
  private val objectMapper = jacksonObjectMapper()
  private val authenticator = Authenticator(application, credentialManager)
  private val buildConfig =
    services.requireService(BuildConfigurationServiceType::class.java)
  private val steps: TaskRecorderType = TaskRecorder.create()
  private var registering: Boolean = false

  val isRegistering : Boolean
  get() = this.registering



  private fun handleFailure(e: Exception) {
    // TODO Make sure errors are handled properly and communicated to the user properly.
    when (e) {
      is CreatePublicKeyCredentialDomException -> {
        // Handle the passkey DOM errors thrown according to the
        // WebAuthn spec.
        logger.error("CreatePublicKeyCredentialDomException")
        steps.currentStepFailed(e.message?:"Credential Manager Error", e.domError.type)
        //handlePasskeyError(e.domError)
      }
      is CreateCredentialCancellationException -> {
        // The user intentionally canceled the operation and chose not
        // to register the credential.
        logger.error("CreateCredentialCancellationException")
        steps.currentStepSucceeded("User cancelled request")
      }
      is CreateCredentialInterruptedException -> {
        // Retry-able error. Consider retrying the call.
        logger.error("CreateCredentialInterruptedException")
        steps.currentStepFailed(e.message?:"Credential Manager Error", "")
      }
      is CreateCredentialProviderConfigurationException -> {
        // Your app is missing the provider configuration dependency.
        // Most likely, you're missing the
        // "credentials-play-services-auth" module.
        logger.error("CreateCredentialProviderConfigurationException")
        steps.currentStepFailed(e.message?:"Credential Manager Error", "")
      }
      is CreateCredentialUnknownException -> {
        //TODO alternate passkey procedures.
        //This may occur when device does not have Credential Manager enabled
        //e.g. in Oneplus 12R it was tested and error msg was:
        //androidx.credentials.exceptions.CreateCredentialUnknownException: Failed resolution of:
        //Lcom/google/android/gms/fido/fido2/api/common/ErrorCode
        //theory is it is trying to use a backup method using fido2 api,
        // so implementing fido2 when such message is given may be valid way use passkeys on those devices
        logger.error("CreateCredentialUnknownException")
        steps.currentStepFailed(e.message?:"Unknown Error", "")
      }
      is CreateCredentialCustomException -> {
        // You have encountered an error from a 3rd-party SDK. If you
        // make the API call with a request object that's a subclass of
        // CreateCustomCredentialRequest using a 3rd-party SDK, then you
        // should check for any custom exception type constants within
        // that SDK to match with e.type. Otherwise, drop or log the
        // exception.
        logger.error("CreateCredentialCustomException type={}, message={}",e.type, e.message)
        steps.currentStepFailed(e.message?:"Credential Manager Error", "")
      }
      is GetCredentialUnsupportedException -> {
        logger.error("GetCredentialUnsupportedException", e)
        steps.currentStepFailed(e.message?:"Credentials not Supported", "")
      }
      is PasskeyFinishException -> {
        logger.error("PasskeyFinishException",e)
        steps.currentStepFailed("${e.message}: ${e.responseProperties.status}", e.message?:"")
      }
      else -> {
        logger.error("Unexpected exception type ${e::class.java.name}: ${e.message}")
        steps.currentStepFailed(e.javaClass.name, "")
      }
    }

    this.profiles.profileAccountLogin(
      ProfileAccountLoginRequest.EkirjastoCancel(
        accountId = account,
        description = description,
        registering = this.registering
      )
    )

  }

  fun passkeyLogin() {
    this.registering = false
    this.viewModelScope.launch(Dispatchers.IO) {
      lateinit var startResponse : AuthenticatePublicKey
      lateinit var challengeResponse : AuthenticateResult
      try {
        steps.beginNewStep("Passkey Login Start")
        logger.debug("Passkey Login Start")
        startResponse = requestPasskeyLoginStart()
        logger.debug("Passkey Login Start responded")
        steps.currentStepSucceeded("Login start request OK")
      } catch (e: Exception) {
        handleFailure(e)
        passkeyResult.postValue(steps.finishFailure())
        return@launch
      }

      try {
        logger.debug("Passkey Login Challenge")
        steps.beginNewStep("Authenticator Challenge")
        challengeResponse = requestPasskeyLoginChallenge(startResponse)
        logger.debug("Passkey Login Challenge responded")
        steps.currentStepSucceeded("Authentication completed")
      } catch (e: Exception) {
        handleFailure(e)
        passkeyResult.postValue(steps.finishFailure())
        return@launch
      }

      when (challengeResponse) {

        is AuthenticateResult.Success -> {
          try {
            logger.debug("Passkey Login Complete")
            steps.beginNewStep("Complete Login")
            val auth = requestPasskeyLoginComplete(challengeResponse)
            steps.currentStepSucceeded("Complete Login succeeded")
            passkeyResult.postValue(steps.finishSuccess(auth))
          } catch (e: Exception) {
            handleFailure(e)
            passkeyResult.postValue(steps.finishFailure())
            return@launch
          }
        }

        is AuthenticateResult.Failure -> {
          logger.warn("Authenticator result Failed")
          steps.currentStepFailed(
            challengeResponse.message,
            "AuthenticatorError",
            challengeResponse.error
          )
          passkeyResult.postValue(steps.finishFailure())
        }
      }
    }
  }

  private fun requestPasskeyLoginStart(): AuthenticatePublicKey {
    //val data = mapToJson(mapOf("username" to username))
    val startRequest = createPostRequest(description.passkey_login_start, null)
    val requestResponse = sendRequest(startRequest)
    val response: JsonNode
    when (val status = requestResponse.status){
      is LSHTTPResponseStatus.Responded.OK -> {
        response = bodyAsJsonNode(status.bodyStream!!)
      }
      is LSHTTPResponseStatus.Responded.Error -> {
        throw Exception("Passkey Login Start Error: ${status.properties.status}")
      }
      is LSHTTPResponseStatus.Failed -> {
        throw Exception("Passkey Login Start Failed",status.exception)
      }
    }

    logger.warn("passkey login start response: ${response.toPrettyString()}")

    return objectMapper.readValue(response["publicKey"].toString())
  }

  private suspend fun requestPasskeyLoginChallenge(publicKey: AuthenticatePublicKey): AuthenticateResult {

    return authenticator.authenticate(
      AuthenticateParameters(
        relyingPartyId = publicKey.rpId,
        challenge = publicKey.challenge,
        timeout = publicKey.timeout,
        userVerification = publicKey.userVerification,
        allowCredentials = publicKey.allowCredentials
      )
    )

  }

  private fun requestPasskeyLoginComplete(authResult: AuthenticateResult.Success) : PasskeyAuth{
    val data: AuthenticateFinishRequest = AuthenticateFinishRequest.fromAuthenticationResult(authResult)
    val dataJson = this.objectMapper.writeValueAsString(data)
    val jsonNode = objectMapper.createObjectNode()
    jsonNode.put("id", data.id)
    jsonNode.replace("data", objectMapper.readTree(dataJson))
    this.logger.debug("Passkey Login Finish Request: {}", dataJson)
    val requestBody: String = objectMapper.writeValueAsString(jsonNode)
    val response = sendRequest(createPostRequest(description.passkey_login_finish, requestBody))
    val responseBodyNode: JsonNode?
    when (val status=response.status) {
      is LSHTTPResponseStatus.Responded.OK -> {
        responseBodyNode = bodyAsJsonNode(status.bodyStream!!)
      }
      is LSHTTPResponseStatus.Responded.Error -> {
        throw PasskeyFinishException("Login Finish request error",status.properties)
      }
      is LSHTTPResponseStatus.Failed -> {
        throw status.exception

      }
    }

    val token = responseBodyNode["token"].asText()
    val exp = responseBodyNode["exp"].asLong()
    return PasskeyAuth(token,exp)
  }

  val passkeyResult = MutableLiveData<TaskResult<PasskeyAuth>>()

  fun passkeyRegister() {
    this.registering = true
    viewModelScope.launch(Dispatchers.IO) {
      val uri = description.passkey_register_start
      val body = mapToJson(mapOf("username" to ""))
      lateinit var registerStartResponse: JsonNode
      lateinit var challengeResponse: RegisterResult
      lateinit var authResponse: PasskeyAuth
      try {
        logger.debug("Register Start")
        steps.beginNewStep("Passkey Register Start")
        registerStartResponse = requestPasskeyRegisterStart(uri, body)
        logger.warn(registerStartResponse.toPrettyString())
        logger.debug("Register Start Complete")
        steps.currentStepSucceeded("Passkey Register Start Success")
      } catch (e: Exception) {
        handleFailure(e)
        passkeyResult.postValue(steps.finishFailure())
        return@launch
      }

      try {
        logger.debug("Register Challenge")
        steps.beginNewStep("Passkey Register Challenge Start")
        challengeResponse = startPasskeyRegisterChallenge(registerStartResponse)
        logger.debug("Register Challenge Complete")
        steps.currentStepSucceeded("Passkey Register Challenge completed")
      } catch (e: Exception) {
        handleFailure(e)
        passkeyResult.postValue(steps.finishFailure())
        return@launch
      }

      try {
        logger.debug("Register Finish")
        steps.beginNewStep("Passkey Register Finish start")
        authResponse = passkeyRegisterFinish(challengeResponse)
        logger.debug("Register Finish Complete")
        steps.currentStepSucceeded("Passkey Register Finished successfully")
      } catch (e: Exception) {
        handleFailure(e)
        passkeyResult.postValue(steps.finishFailure())
        return@launch
      }
      passkeyResult.postValue(steps.finishSuccess(authResponse))
    }

  }

  private fun requestPasskeyRegisterStart(uri: URI, body: String): JsonNode {
    val httpRequest = createAuthorizedPostRequest(uri, body)
    val response = sendRequest(httpRequest)
    when (val status = response.status){
      is LSHTTPResponseStatus.Responded.OK -> status.bodyStream?.let{
        return bodyAsJsonNode(it)
      }
      else -> throw Exception("Passkey Register Start request failed: $status")
    }
    throw Exception("requestPasskeyRegisterStart unknown error")
  }

  private suspend fun startPasskeyRegisterChallenge(jsonBody: JsonNode): RegisterResult {
    logger.debug("Start Passkey Register Challenge")
    val publicKeyJsonNode = jsonBody.get("publicKey")

//    val params: RegisterParameters = objectMapper.readValue(publicKeyJsonNode.toString())
    // try custom configuration instead of server's provided parameters
    val params = RegisterParameters.from(jsonBody)
    return authenticator.register(params)
  }

  private fun passkeyRegisterFinish(registerResult: RegisterResult): PasskeyAuth {
    val body = FinishRegisterRequest(
      username = "",
      data = RegisterSignedChallengeRequest(
        id = registerResult.id,
        rawId = registerResult.rawId,
        response = registerResult.response,
        type = registerResult.type
      )
    )
    val json = objectMapper.writeValueAsString(body)
    val node = objectMapper.readTree(json)
    this.logger.debug("Passkey Register Finish Request: {}", node.toPrettyString())
    val uri = description.passkey_register_finish
    val request = createAuthorizedPostRequest(uri, json)
    val response = sendRequest(request)
    this.logger.debug("Response status: {}", response.status)
    response.use {
      when (val status = response.status){
        is LSHTTPResponseStatus.Responded.OK -> {
          val responseBody: JsonNode = bodyAsJsonNode(status.bodyStream!!)
          return PasskeyAuth(
            token = responseBody["token"].asText(),
            exp = responseBody["exp"].asLong()
          )
        }
        is LSHTTPResponseStatus.Responded.Error -> {
          throw Exception("Register Finish Error: $status")
        }
        is LSHTTPResponseStatus.Failed -> {
          throw status.exception
        }
      }
    }
  }

  private fun createPostRequest(uri: URI, body: String?): LSHTTPRequestType {
    val bodyString = body ?:""
    return this.http.newRequest(uri)
      .setMethod(Post(
        bodyString.toByteArray(Charset.forName("UTF-8")),
        MIMEType("application", "json", mapOf())
      ))
      .addHeader("accept","application/json")
      .build()
  }

  private fun createAuthorizedPostRequest(uri: URI, body: String?): LSHTTPRequestType {
    var bodyString = ""
    body?.let { bodyString = it }
    return this.http.newRequest(uri)
      .setMethod(Post(
        bodyString.toByteArray(Charset.forName("UTF-8")),
        MIMEType("application", "json", mapOf())
      ))
      .setAuthorization(LSHTTPAuthorizationBearerToken.ofToken(circulationToken!!))
      .addHeader("accept","json")
      .build()
  }
  private fun sendRequest(request: LSHTTPRequestType): LSHTTPResponseType {

    val response = request.execute()
    when (val status = response.status) {
      is LSHTTPResponseStatus.Responded.OK -> {
        logger.debug("Response OK")
      }

      is LSHTTPResponseStatus.Responded.Error -> {
        val body = status.bodyStream?.let{ bodyAsJsonNode(it)}
        logger.error("Request Error: {} {}", status.properties.status, body?.toPrettyString())
      }

      is LSHTTPResponseStatus.Failed -> {
        logger.error("Request Failed")
      }
    }
    return response
  }
  private fun bodyAsJsonNode(input: InputStream): JsonNode {
    return objectMapper.readTree(input)
  }
  private fun mapToJson(map: Map<String, String>): String {
    return this.objectMapper.writeValueAsString(map)
  }

  val supportEmailAddress: String =
    buildConfig.supportErrorReportEmailAddress
}
