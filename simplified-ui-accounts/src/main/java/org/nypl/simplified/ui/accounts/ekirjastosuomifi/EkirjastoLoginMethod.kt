package org.nypl.simplified.ui.accounts.ekirjastosuomifi

import org.nypl.simplified.accounts.api.AccountUsername

sealed class EkirjastoLoginMethod {
  class SuomiFi() : EkirjastoLoginMethod()

  data class Passkey(
    val loginState: LoginState,
    val circulationToken: String?,
    val username: AccountUsername?
  ) : EkirjastoLoginMethod(){
    enum class LoginState { RegisterUnavailable, RegisterAvailable, LoggingIn, LoggedIn}
  }
}
