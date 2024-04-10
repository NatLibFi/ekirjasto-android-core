package org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.authenticate

import androidx.credentials.PublicKeyCredential
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
sealed class AuthenticateResult {

  companion object {
    fun parseJson(cred: PublicKeyCredential) : AuthenticateResult {
      val mapper = jacksonObjectMapper()
      val json: JsonNode = mapper.readTree(cred.authenticationResponseJson)
      val response = json["response"]
      return Success(
        id = json["id"].asText(),
        rawId =  json["rawId"].asText(),
        clientDataJSON = response["clientDataJSON"].asText(),
        userHandle = response["userHandle"].asText(),
        signature = response["signature"].asText(),
        authenticatorData = response["authenticatorData"].asText(),
      )
    }
  }

  data class Failure(
    val message: String,
    val error: Exception?
  ) : AuthenticateResult()
  data class Success(
    val id: String,
    val rawId: String,
    val clientDataJSON: String,
    val userHandle: String,
    val signature: String,
    val authenticatorData: String,

  ) : AuthenticateResult()

}
