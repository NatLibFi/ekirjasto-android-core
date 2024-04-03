package org.nypl.simplified.ui.accounts.ekirjastopasskey

import android.app.Application
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialCustomException
import androidx.credentials.exceptions.CreateCredentialInterruptedException
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.lifecycle.ViewModel
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import one.irradia.mime.api.MIMEType
import org.librarysimplified.http.api.LSHTTPAuthorizationBearerToken
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.services.api.Services
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.librarysimplified.http.api.LSHTTPRequestBuilderType.Method.Post
import org.librarysimplified.http.api.LSHTTPRequestType
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.librarysimplified.http.api.LSHTTPResponseType
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels.PasskeyAuth
import org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels.authenticate.AuthenticateFinishRequest
import org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels.register.RegisterResult
import org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels.register.FinishRegisterRequest
import org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels.register.RegisterParameters
import org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels.register.RegisterSignedChallengeRequest
import org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels.authenticate.AuthenticateParameters
import org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels.authenticate.AuthenticatePublicKey
import org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels.authenticate.AuthenticateResult
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URI
import java.nio.charset.Charset

class AccountEkirjastoPasskeyViewModel (
  private val application: Application,
  private val account: AccountID,
  private val description: AccountProviderAuthenticationDescription.Ekirjasto,
  // Used for register passkey
  private val circulationToken: String?,
  // Used for passkey login
  private val credentialManager: CredentialManager
) : ViewModel() {

  private val services = Services.serviceDirectory()
  private val profiles = services.requireService(ProfilesControllerType::class.java)
  private val http = this.services.requireService(LSHTTPClientType::class.java)
  private val logger = LoggerFactory.getLogger(AccountEkirjastoPasskeyViewModel::class.java)
  private val objectMapper = jacksonObjectMapper()
  private val authenticator = Authenticator(application, credentialManager)

  //TODO internal events

  private fun handleFailure(e: Exception) {
    // TODO Make sure errors are handled properly and communicated to the user properly.
    when (e) {
      is CreatePublicKeyCredentialDomException -> {
        // Handle the passkey DOM errors thrown according to the
        // WebAuthn spec.
        logger.error("CreatePublicKeyCredentialDomException")
        //handlePasskeyError(e.domError)
      }
      is CreateCredentialCancellationException -> {
        // The user intentionally canceled the operation and chose not
        // to register the credential.
        logger.error("CreateCredentialCancellationException")
      }
      is CreateCredentialInterruptedException -> {
        // Retry-able error. Consider retrying the call.
        logger.error("CreateCredentialInterruptedException")
      }
      is CreateCredentialProviderConfigurationException -> {
        // Your app is missing the provider configuration dependency.
        // Most likely, you're missing the
        // "credentials-play-services-auth" module.
        logger.error("CreateCredentialProviderConfigurationException")
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
      }
      is CreateCredentialCustomException -> {
        // You have encountered an error from a 3rd-party SDK. If you
        // make the API call with a request object that's a subclass of
        // CreateCustomCredentialRequest using a 3rd-party SDK, then you
        // should check for any custom exception type constants within
        // that SDK to match with e.type. Otherwise, drop or log the
        // exception.
        logger.error("CreateCredentialCustomException type={}, message={}",e.type, e.message)

      }
      else -> {
        logger.error("Unexpected exception type ${e::class.java.name}: ${e.message}")
        logger.error(e.stackTraceToString())
      }
    }


    //TODO post internal event for passkey failed
    //this.postPasskeyFailed(e)
  }

  suspend fun passkeyLogin(username: String): PasskeyAuth {

    lateinit var startResponse : AuthenticatePublicKey
    lateinit var challengeResponse : AuthenticateResult
    try {
      startResponse = requestPasskeyLoginStart(username)
      this.logger.debug("Passkey Login Start response= {}",startResponse)
    } catch (e: Exception){
      handleFailure(e)
      return PasskeyAuth.Fail(e)
    }

    try {
      challengeResponse = requestPasskeyLoginChallenge(startResponse)
      this.logger.debug("Passkey Login Challenge response = {}", challengeResponse)
    } catch (e: Exception) {
      handleFailure(e)
      return PasskeyAuth.Fail(e)

    }

    when (challengeResponse) {

      is AuthenticateResult.Success -> {
        try {
          val auth = requestPasskeyLoginComplete(challengeResponse)
          if (auth.success){
            postPasskeyComplete(username, auth)
          }
          return auth
        } catch (e: Exception) {
          handleFailure(e)
          return PasskeyAuth.Fail(e)
        }
      }
      is AuthenticateResult.Failure -> {
        logger.debug("Authenticator result Failed")
        return PasskeyAuth.Fail(Exception("Fail"))
      }
    }
  }

  private fun postPasskeyComplete(username: String, auth: PasskeyAuth) {
    this.logger.warn("Posting Passkey Completed event --  is this necessary?")
//    this.profiles.profileAccountLogin(
//      ProfileAccountLoginRequest.EkirjastoComplete(
//        accountId = this.account,
//        description = this.description,
//        ekirjastoToken = auth.token,
//        username = username
//      )
//    )
  }

  suspend fun requestPasskeyLoginStart(username: String): AuthenticatePublicKey {
    val data = mapToJson(mapOf("username" to username))
    var startRequest = createPostRequest(description.passkey_login_start, null) //TODO ?
    var requestResponse = sendRequest(startRequest)
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
    this.logger.debug("RequestPassKeyLoginStart response ={}",response.toPrettyString())


    return objectMapper.readValue(response["publicKey"].toString())
  }

  private suspend fun requestPasskeyLoginChallenge(publicKey: AuthenticatePublicKey): AuthenticateResult {

    val result = authenticator.authenticate(AuthenticateParameters(
      relyingPartyId = publicKey.rpId,
      challenge = publicKey.challenge,
      timeout = publicKey.timeout,
      userVerification = publicKey.userVerification,
      allowCredentials = publicKey.allowCredentials
    ))

    return result

  }

  private suspend fun requestPasskeyLoginComplete(authResult: AuthenticateResult.Success) : PasskeyAuth{
    val data: AuthenticateFinishRequest = AuthenticateFinishRequest.fromAuthenticationResult(authResult)
    val dataJson = this.objectMapper.writeValueAsString(data)
    val jsonNode = objectMapper.createObjectNode()
    jsonNode.put("id", data.id)
    jsonNode.replace("data", objectMapper.readTree(dataJson))
    val requestBody: String = objectMapper.writeValueAsString(jsonNode)
    logger.warn("passkeyLoginComplete requestBody= ${jsonNode.toPrettyString()}")
    var response = sendRequest(createPostRequest(description.passkey_login_finish, requestBody))
    var responseBodyNode: JsonNode?
    when (val status=response.status){
      is LSHTTPResponseStatus.Responded.OK -> {
        responseBodyNode = bodyAsJsonNode(status.bodyStream!!)
      }
      else -> {
        throw Exception("Login Finish request failed")
      }
    }

    responseBodyNode.let {
      val token = it["token"].asText()
      val exp = it["exp"].asLong()
      if (token != null) {
        return PasskeyAuth.Ok(token,exp)
      }
    }
    throw Exception("Passkey Login Complete Failed")
  }

  suspend fun passkeyRegister(username: String):PasskeyAuth {
    val uri = description.passkey_register_start
    val body = mapToJson(mapOf("username" to username))
    lateinit var registerStartResponse: JsonNode
    lateinit var challengeResponse: RegisterResult
    lateinit var authResponse: PasskeyAuth

    try {
      logger.debug("Register Start")
      registerStartResponse = requestPasskeyRegisterStart(uri, body)
      logger.debug("Register Start Complete")
    } catch (e: Exception) {
      handleFailure(e)
    }

    try {
      logger.debug("Register Challenge")
      challengeResponse = startPasskeyRegisterChallenge(username, registerStartResponse)
      logger.debug("Register Challenge Complete")
    } catch (e: Exception) {
      handleFailure(e)
    }

    try {
      logger.debug("Register Finish")
      authResponse = passkeyRegisterFinish(username, challengeResponse)
      logger.debug("Register Finish Complete: $authResponse")
    } catch (e: Exception){
      handleFailure(e)
    }

    if (authResponse.success){
      postPasskeyComplete(username, authResponse)
    }

    return authResponse
  }

  private suspend fun requestPasskeyRegisterStart(uri: URI, body: String): JsonNode {
    val httpRequest = createAuthorizedPostRequest(uri, body)
    val response = sendRequest(httpRequest)
    when (val status = response.status){
      is LSHTTPResponseStatus.Responded.OK -> status.bodyStream?.let{
        return bodyAsJsonNode(it)
      }
      else -> throw Exception("Passkey Register Start request failed: $status", )
    }
    throw Exception("requestPasskeyRegisterStart unknown error")
  }

  private suspend fun startPasskeyRegisterChallenge(username: String, jsonBody: JsonNode): RegisterResult {
    logger.debug("Start Passkey Register Challenge. User={}, challenge={}", username, jsonBody.toPrettyString())
    val publicKeyJsonNode = jsonBody.get("publicKey")
    val params: RegisterParameters = objectMapper.readValue(publicKeyJsonNode.toString())
    return authenticator.register(params)
  }

  private fun passkeyRegisterFinish(username: String, registerResult: RegisterResult): PasskeyAuth {
    val body = FinishRegisterRequest(
      username = username,
      data = RegisterSignedChallengeRequest(
        id = registerResult.id,
        rawId = registerResult.rawId,
        response = registerResult.response,
        type = registerResult.type
      )
    )
    val uri = description.passkey_register_finish
    val request = createAuthorizedPostRequest(uri, objectMapper.writeValueAsString(body))
    val response = sendRequest(request)
    this.logger.debug("Response status: ${response.status}")
    response.use {
      when (val status = response.status){
        is LSHTTPResponseStatus.Responded.OK -> {
          val responseBody: JsonNode = bodyAsJsonNode(status.bodyStream!!)
          this.logger.debug("Response body: ${responseBody.toPrettyString()}")
          return PasskeyAuth(
            success = responseBody["token"].asText() != null,
            token = responseBody["token"].asText(),
            exp = responseBody["exp"].asLong()
          )
        }
        else -> throw Exception("Passkey Register Finish request failed: $status")
      }
    }
  }

  private fun createPostRequest(uri: URI, body: String?): LSHTTPRequestType {
    val bodyString = body ?:""
    logger.warn("post: $uri, $bodyString")
    return this.http.newRequest(uri)
      .setMethod(Post(
        bodyString.toByteArray(Charset.forName("UTF-8")),
        MIMEType("application", "json", mapOf())
      ))
      .addHeader("accept","json")
      .build()
  }

  private fun createAuthorizedPostRequest(uri: URI, body: String?): LSHTTPRequestType {
    var bodyString = "";
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
    val node = this.objectMapper.readTree(input)
    return node
  }
  private fun mapToJson(map: Map<String, String>): String {
    return this.objectMapper.writeValueAsString(map)
  }
}
