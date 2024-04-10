package org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.register

data class PublicKeyCredentialParameters(
  val type: String,
  val alg: Int)
