package org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels.authenticate

data class AuthenticatePublicKey(
  val rpId: String,
  val challenge: String,
  val timeout: Long,
  val userVerification: String,
  val allowCredentials: List<AllowCredentialType>
)

