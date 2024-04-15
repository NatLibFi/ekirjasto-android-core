package org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.register

data class RegisterSignedChallengeRequest(
  val id: String,
  val rawId: String,
  val response: RegisterChallengeRequestResponse,
  val type: String = "public-key"
)
