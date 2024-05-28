package org.nypl.simplified.ui.accounts.ekirjasto.suomifi

import java.io.Serializable

sealed class EkirjastoLoginMethod : Serializable {
  class SuomiFi() : EkirjastoLoginMethod()

  data class Passkey(
    val loginState: LoginState,
    val circulationToken: String?,
  ) : EkirjastoLoginMethod(){
    enum class LoginState { RegisterUnavailable, RegisterAvailable, LoggingIn, LoggedIn}
  }

}
