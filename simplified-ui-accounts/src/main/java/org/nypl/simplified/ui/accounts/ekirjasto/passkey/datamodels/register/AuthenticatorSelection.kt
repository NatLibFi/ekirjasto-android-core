package org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.register

data class AuthenticatorSelection(
  val authenticatorAttachment: String,
  val userVerification: String,
  val residentKey: String,
  val requireResidentKey: Boolean
)
