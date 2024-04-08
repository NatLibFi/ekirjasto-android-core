package org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels

import org.nypl.simplified.taskrecorder.api.TaskRecorderType

data class PasskeyAuth(
  val token: String,
  val exp: Long,
)
