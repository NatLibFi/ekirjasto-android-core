package org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.register

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties("authenticatorData", "publicKeyAlgorithm", "publicKey", "transports")
data class RegisterChallengeRequestResponse (
  val clientDataJSON: String,
  val attestationObject: String,
//  val transports: List<String>
)
