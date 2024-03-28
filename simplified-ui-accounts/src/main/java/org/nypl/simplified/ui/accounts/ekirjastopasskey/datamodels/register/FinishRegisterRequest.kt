package org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels.register

data class FinishRegisterRequest(
  val username: String,
  val data: RegisterSignedChallengeRequest
)
