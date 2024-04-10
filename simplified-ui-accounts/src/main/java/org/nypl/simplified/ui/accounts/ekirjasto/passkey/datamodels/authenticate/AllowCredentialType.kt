package org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.authenticate

data class AllowCredentialType(
  val type: String,
  val id: String,
  val transports: List<String>
)
