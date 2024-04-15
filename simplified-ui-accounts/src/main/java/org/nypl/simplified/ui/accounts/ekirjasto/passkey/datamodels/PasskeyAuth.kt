package org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels


data class PasskeyAuth(
  val token: String,
  val exp: Long,
)
