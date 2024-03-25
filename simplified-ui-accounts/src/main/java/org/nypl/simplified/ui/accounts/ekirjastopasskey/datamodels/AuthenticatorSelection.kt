package org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels

data class AuthenticatorSelection(
  val authenticatorAttachment: String,
  val userVerification: String,
  val residentKey: String,
  val requireResidentKey: Boolean
)
