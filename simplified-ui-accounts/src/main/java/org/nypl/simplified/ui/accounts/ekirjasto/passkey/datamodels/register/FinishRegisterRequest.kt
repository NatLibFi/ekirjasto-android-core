package org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.register

data class FinishRegisterRequest(
  val username: String,
  val data: RegisterSignedChallengeRequest
)
