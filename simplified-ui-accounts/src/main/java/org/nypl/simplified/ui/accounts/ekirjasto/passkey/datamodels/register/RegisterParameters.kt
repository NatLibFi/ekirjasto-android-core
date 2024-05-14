package org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.register

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

data class RegisterParameters(
  val challenge: String,
  val rp: RelyingPartyType,
  val user: User,
  val pubKeyCredParams: List<PublicKeyCredentialParameters>,
  val timeout: Int,
  val attestation: String,
  val authenticatorSelection: AuthenticatorSelection,
) {
  companion object {
    fun from(jsonBody: JsonNode): RegisterParameters {
      val objectMapper = jacksonObjectMapper()
      val publicKey = jsonBody["publicKey"]
      return RegisterParameters(
        challenge = publicKey["challenge"].asText(),
        rp = objectMapper.readValue(publicKey["rp"].toString()),
        user = objectMapper.readValue(publicKey["user"].toString()),
        pubKeyCredParams = objectMapper.readValue(publicKey["pubKeyCredParams"].toString()),
        timeout = publicKey["timeout"].asInt(),
        attestation = publicKey["attestation"].asText(),
        authenticatorSelection = AuthenticatorSelection(
          authenticatorAttachment = "platform",
          requireResidentKey = true,
          residentKey = "required",
          userVerification = "required"
        )
      )
    }
  }
}

