package org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels

data class PublicKey(
  val challenge: String,
  val rp: RelyingPartyType,
  val user: User,
  val pubKeyCredParams: List<PublicKeyCredentialParameters>,
  val timeout: Int,
  val attestation: String,
  val authenticatorSelection: AuthenticatorSelection,
  )
