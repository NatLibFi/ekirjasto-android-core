package org.nypl.simplified.ui.accounts.ekirjasto.passkey

import android.app.Activity
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.authenticate.AuthenticateParameters
import org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.authenticate.AuthenticateResult
import org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.authenticate.PublicKeyCredentialRequestOptions
import org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.register.RegisterChallengeRequestResponse
import org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.register.RegisterParameters
import org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.register.RegisterResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Wrapper for android credential manager
 */
class Authenticator(
  val application: Activity,
  val credentialManager: CredentialManager
) {
  val objectMapper = jacksonObjectMapper()
  val logger: Logger = LoggerFactory.getLogger(Authenticator::class.java)

  suspend fun authenticate(parameters: AuthenticateParameters): AuthenticateResult {
    val options = PublicKeyCredentialRequestOptions.from(parameters)
    val credOption = GetPublicKeyCredentialOption(objectMapper.writeValueAsString(options))
    val request = GetCredentialRequest.Builder()
      .addCredentialOption(credOption)
      .build()
    val result: GetCredentialResponse?
    result = credentialManager.getCredential(application, request)
    result.let {
      when (val cred = it.credential) {
        is PublicKeyCredential -> {
          return AuthenticateResult.parseJson(cred)
        }

        else -> throw Exception("Invalid credential type: ${cred.javaClass.name}")
      }
    }
  }

  suspend fun register(parameters: RegisterParameters): RegisterResult {


    lateinit var responseJson: JsonNode
    val createPublicKeyCredentialRequest = CreatePublicKeyCredentialRequest(
      requestJson = objectMapper.writeValueAsString(parameters)
    )

    val result = credentialManager.createCredential(
      context = application,
      request = createPublicKeyCredentialRequest,
    )
    val response: String =
      result.data.getString("androidx.credentials.BUNDLE_KEY_REGISTRATION_RESPONSE_JSON", null)
    logger.debug("Authenticator Response: {}", response)
    responseJson = this.objectMapper.readValue(response)

    return RegisterResult(
      id = responseJson["id"].asText(),
      rawId = responseJson["rawId"].asText(),
      response = this.objectMapper.readValue<RegisterChallengeRequestResponse>(responseJson["response"].toString()),
      type = responseJson["type"].asText()
    )
  }

}
