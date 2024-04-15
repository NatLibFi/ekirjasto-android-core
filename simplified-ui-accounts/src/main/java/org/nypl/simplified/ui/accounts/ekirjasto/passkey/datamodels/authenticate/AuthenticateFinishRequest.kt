package org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.authenticate

data class AuthenticateFinishRequest(
  val type: String = "public-key",
  val id: String,
  val rawId: String,
  val response: AuthenticateResultResponse
){
  data class AuthenticateResultResponse(
    val clientDataJSON: String,
    val userHandle: String,
    val signature: String,
    val authenticatorData: String,
  )

  companion object {
    fun fromAuthenticationResult(result: AuthenticateResult.Success): AuthenticateFinishRequest
    {
      return AuthenticateFinishRequest(
        id = result.id,
        rawId = result.rawId,
        response = AuthenticateResultResponse(
          clientDataJSON = result.clientDataJSON,
          userHandle = result.userHandle,
          signature = result.signature,
          authenticatorData = result.authenticatorData
        )
      )
    }
  }
}
