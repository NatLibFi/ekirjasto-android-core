package org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels

/**
 * Response from authenticator. Only includes used fields
 */
data class AuthenticatorResponse(
  val rawId: String,
  val type: String,
  val id: String,
  val response: RegisterChallengeRequestResponse,
)
