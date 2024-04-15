package org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.register

data class RegisterParameters(
  val challenge: String,
  val rp: RelyingPartyType,
  val user: User,
  val pubKeyCredParams: List<PublicKeyCredentialParameters>,
  val timeout: Int,
  val attestation: String,
  val authenticatorSelection: AuthenticatorSelection,
  )
