package org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.register

/**
 * Response from authenticator. Only includes used fields
 */
data class RegisterResult(
  val rawId: String,
  val type: String,
  val id: String,
  val response: RegisterChallengeRequestResponse,
)
