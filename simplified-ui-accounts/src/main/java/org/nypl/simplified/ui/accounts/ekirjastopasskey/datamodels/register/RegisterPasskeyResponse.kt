package org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels.register

import org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels.register.RegisterChallengeRequestResponse

/**
 * Response from authenticator. Only includes used fields
 */
data class RegisterPasskeyResponse(
  val rawId: String,
  val type: String,
  val id: String,
  val response: RegisterChallengeRequestResponse,
)
