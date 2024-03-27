package org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels

data class AuthResponse(
  val success: Boolean,
  val token: String,
  val exp: Long
)
