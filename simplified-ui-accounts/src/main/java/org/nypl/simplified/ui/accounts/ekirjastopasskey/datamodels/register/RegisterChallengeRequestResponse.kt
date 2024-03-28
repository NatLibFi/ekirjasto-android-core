package org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels.register

data class RegisterChallengeRequestResponse (
  val clientDataJSON: String,
  val attestationObject: String,
  val transports: List<String>
)
