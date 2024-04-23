package org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.authenticate

data class AuthenticatePublicKey(
  val rpId: String,
  val challenge: String,
  val timeout: Long,
  val userVerification: String,
  val allowCredentials: List<AllowCredentialType>?
)

