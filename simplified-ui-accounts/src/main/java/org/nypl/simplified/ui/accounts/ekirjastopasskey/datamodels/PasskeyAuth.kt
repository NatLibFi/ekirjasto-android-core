package org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels


data class PasskeyAuth(
  val token: String,
  val exp: Long,
)
