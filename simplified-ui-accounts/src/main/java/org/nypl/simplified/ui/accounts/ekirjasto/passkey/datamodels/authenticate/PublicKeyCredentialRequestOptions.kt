package org.nypl.simplified.ui.accounts.ekirjasto.passkey.datamodels.authenticate

data class PublicKeyCredentialRequestOptions(
  val challenge: String,
  val timeout: Long,
  val rpId: String,
  val allowCredentials: List<AllowCredentialType>,
  val userVerification: String,
  val hints: List<String>,
  val extensions: Map<String,String>
){
  companion object {
    fun from(authParams: AuthenticateParameters): PublicKeyCredentialRequestOptions{
      return PublicKeyCredentialRequestOptions(
        challenge = authParams.challenge,
        timeout = authParams.timeout?:10000,
        rpId = authParams.relyingPartyId,
        userVerification = authParams.userVerification?:"",
        allowCredentials = authParams.allowCredentials?:listOf(),
        hints = emptyList(),
        extensions = emptyMap(),
        )
    }
  }
}
