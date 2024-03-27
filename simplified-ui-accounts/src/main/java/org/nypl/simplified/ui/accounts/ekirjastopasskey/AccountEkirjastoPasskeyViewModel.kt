package org.nypl.simplified.ui.accounts.ekirjastopasskey

import android.app.Application
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialCustomException
import androidx.credentials.exceptions.CreateCredentialInterruptedException
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.lifecycle.ViewModel
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.json.JsonMapper
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
import org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels.AuthResponse
import org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels.AuthenticatorResponse
import org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels.FinishRegisterRequest
import org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels.PublicKey
import org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels.RegisterChallengeRequestResponse
import org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels.RegisterSignedChallengeRequest
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URI
import java.nio.charset.Charset

class AccountEkirjastoPasskeyViewModel (
  private val application: Application,
  private val account: AccountID,
  private val description: AccountProviderAuthenticationDescription.Ekirjasto,
  private val circulationToken: String,
  private val credentialManager: CredentialManager
) : ViewModel() {

  private val services = Services.serviceDirectory()
  private val http = this.services.requireService(LSHTTPClientType::class.java)
  private val logger = LoggerFactory.getLogger(AccountEkirjastoPasskeyFragment::class.java)
  private val objectMapper = jacksonObjectMapper()

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
        logger.warn("Unexpected exception type ${e::class.java.name}")
        logger.error(e.stackTraceToString())
      }
    }

    //this.postPasskeyFailed(e)
  }

  fun createPasskeyAsync(requestJson: String) {
//    val createPublicKeyCredentialRequest = CreatePublicKeyCredentialRequest(
//      requestJson = requestJson
//    )
//
//    try {
//        val result = credentialManager.createCredential(
//        context = requireContext(),
//        request = createPublicKeyCredentialRequest,
//      )
//    } catch (e : CreateCredentialException){
//      handleFailure(e)
//      return@launch
//    }
//
//    // TODO Gather correct json information. Maybe result.data?
//    var result: LSHTTPResponseStatus.Responded.OK? = null
//    try {
//      result = passkeyRequest(
//        parameters.authenticationDescription.passkey_register_finish,
//        "{\"email\": \"email\", \"data\": \"data\"}"
//      )
//    } catch (e : Exception) {
//      handleFailure(e)
//      return@launch
//    }
//
//    finishPasskey(result)

  }

  suspend fun passkeyRegister(username: String):AuthResponse {
    val uri = description.passkey_register_start
    val body = JsonMapper().writeValueAsString(mapOf("username" to username, ))
    try {
      val registerStartResponse = requestPasskeyRegisterStart(uri, body)
      logger.debug("Register Start Complete")
      val challengeResponse = startPasskeyRegisterChallenge(username, registerStartResponse)
      logger.debug("Register Challenge Complete")
      val authResponse = passkeyRegisterFinish(username, challengeResponse)
      logger.debug("Register Finish Complete: $authResponse")
      logger.debug("TODO: handle register finished event? store auth info")
      return authResponse
    } catch (e: Exception) {
      handleFailure(e)
    }
    throw Exception("Passkey Register - unknown error")
  }

  private suspend fun requestPasskeyRegisterStart(uri: URI, body: String): JsonNode {
    val httpRequest = createPostRequest(uri, body)
    val response = sendRequest(httpRequest)
    when (val status = response.status){
      is LSHTTPResponseStatus.Responded.OK -> status.bodyStream?.let{
        return bodyAsJsonNode(it)
      }
      else -> throw Exception("Passkey Register Start request failed: $status", )
    }
    throw Exception("requestPasskeyRegisterStart unknown error")
  }

  private suspend fun startPasskeyRegisterChallenge(username: String, jsonBody: JsonNode): AuthenticatorResponse {
    logger.debug("Start Passkey Register Challenge. User={}, challenge={}", username, jsonBody.toPrettyString())
    val publicKeyJsonNode = jsonBody.get("publicKey")
    logger.debug("Public Key: jsonNode={}",publicKeyJsonNode)
    val publicKey: PublicKey = objectMapper.readValue(publicKeyJsonNode.toString())
    logger.debug("Public Key Deserialized:{}",objectMapper.writeValueAsString(publicKey))
    val createPublicKeyCredentialRequest = CreatePublicKeyCredentialRequest(
      requestJson = objectMapper.writeValueAsString(publicKey)
    )

    try {
      val result = credentialManager.createCredential(
        context = application,
        request = createPublicKeyCredentialRequest,
      )
      val response : String = result.data.getString("androidx.credentials.BUNDLE_KEY_REGISTRATION_RESPONSE_JSON", null)
      logger.debug("Authenticator Response: {}",response)
      val jsonNode: JsonNode = this.objectMapper.readValue(response)
      val responseProperty = jsonNode["response"]
      logger.debug("response property: ${responseProperty}")
      return AuthenticatorResponse(
        id = jsonNode["id"].asText(),
        rawId = jsonNode["rawId"].asText(),
        response = this.objectMapper.readValue<RegisterChallengeRequestResponse>(responseProperty.toString()),
        type = jsonNode["type"].asText()
      )
    } catch (e : Exception) {
      handleFailure(e)
    }
    throw Exception("PasskeyRegisterChallenge exception")
  }

  private fun passkeyRegisterFinish(username: String, authResponse: AuthenticatorResponse): AuthResponse {
    val body = FinishRegisterRequest(
      username = username,
      data = RegisterSignedChallengeRequest(
        id = authResponse.id,
        rawId = authResponse.rawId,
        response = authResponse.response,
        type = authResponse.type
      )
    )
    val uri = description.passkey_register_finish
    val request = createPostRequest(uri, objectMapper.writeValueAsString(body))
    val response = sendRequest(request)
    this.logger.debug("Response status: ${response.status}")
    response.use {
      when (val status = response.status){
        is LSHTTPResponseStatus.Responded.OK -> {
          val responseBody: JsonNode = bodyAsJsonNode(status.bodyStream!!)
          this.logger.debug("Response body: ${responseBody.toPrettyString()}")
          return AuthResponse(
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
    var bodyString = "";
    body?.let { bodyString = it }
    return this.http.newRequest(uri)
      .setMethod(Post(
        bodyString.toByteArray(Charset.forName("UTF-8")),
        MIMEType("application", "json", mapOf())
      ))
      .setAuthorization(LSHTTPAuthorizationBearerToken.ofToken(circulationToken))
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
        logger.error("Request Error: {}", status.properties?.status)
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
}
