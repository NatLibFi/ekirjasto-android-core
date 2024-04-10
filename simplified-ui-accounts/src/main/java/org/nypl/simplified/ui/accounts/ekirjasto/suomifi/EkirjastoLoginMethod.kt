package org.nypl.simplified.ui.accounts.ekirjasto.suomifi

sealed class EkirjastoLoginMethod {
  class SuomiFi() : EkirjastoLoginMethod()

  data class Passkey(
    val loginState: LoginState,
    val circulationToken: String?,
  ) : EkirjastoLoginMethod(){
    enum class LoginState { RegisterUnavailable, RegisterAvailable, LoggingIn, LoggedIn}
  }

}
