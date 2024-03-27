package org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels

data class RegisterChallengeRequestResponse (
  val clientDataJSON: String,
  val attestationObject: String,
  val transports: List<String>
)
