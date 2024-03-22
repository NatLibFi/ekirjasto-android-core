package org.nypl.simplified.ui.accounts.ekirjastopasskey

import android.app.Application
import android.credentials.CreateCredentialException
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
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
  private val objectMapper = ObjectMapper()

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

  suspend fun passkeyRegister(username: String) {
    val uri = description.passkey_register_start
    val body = JsonMapper().writeValueAsString(mapOf("username" to username, ))
    val httpRequest = postRequest(uri, body)
    val response = sendRequest(httpRequest)
    when (val status = response.status){
      is LSHTTPResponseStatus.Responded.OK -> status.bodyStream?.let{
        startPasskeyRegisterChallenge(username, bodyAsJsonNode(it))
      }
      else -> logger.debug("Passkey Register. Unhandled response status: {}", status)
    }
  }

  private suspend fun startPasskeyRegisterChallenge(username: String, challengeJson: JsonNode) {
    logger.debug("Start Passkey Register Challenge. User={}, challenge={}", username, challengeJson.toPrettyString())
    val createPublicKeyCredentialRequest = CreatePublicKeyCredentialRequest(
      requestJson = challengeJson.toString()
    )


    try {
      val result = credentialManager.createCredential(
        context = application,
        request = createPublicKeyCredentialRequest,
      )
      logger.debug("Credential Manager Result: {}",result.data)
    } catch (e : Exception){
      logger.error(e.stackTraceToString())
    }

  }

  private fun postRequest(uri: URI, body: String?): LSHTTPRequestType {
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
