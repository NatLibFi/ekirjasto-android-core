package org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels

data class FinishRegisterRequest(
  val username: String,
  val data: RegisterSignedChallengeRequest
)
