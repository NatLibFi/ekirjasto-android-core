package org.nypl.simplified.ui.accounts.ekirjastosuomifi

import org.nypl.simplified.accounts.api.AccountUsername
import org.nypl.simplified.ui.accounts.view_bindings.ViewsForEkirjasto

sealed class EkirjastoLoginMethod {
  class SuomiFi() : EkirjastoLoginMethod()

  data class Passkey(
    val loginState: LoginState,
    val token: String?,
    val username: AccountUsername?
  ) : EkirjastoLoginMethod(){
    enum class LoginState { RegisterUnavailable, RegisterAvailable, Registered, LoggedIn}
  }
}
