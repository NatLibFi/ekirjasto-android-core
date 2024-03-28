package org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels.authenticate

data class AuthenticateParameters(
  val relyingPartyId: String,
  val challenge: String,
  val timeout: Long?,
  val userVerification: String?,
  val allowCredentials: List<AllowCredentialType>,
)
