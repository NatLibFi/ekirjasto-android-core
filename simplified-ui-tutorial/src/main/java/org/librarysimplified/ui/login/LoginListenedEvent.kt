package org.librarysimplified.ui.login

import org.nypl.simplified.ui.accounts.ekirjasto.suomifi.AccountEkirjastoSuomiFiEvent

sealed class LoginListenedEvent {
  data class SuomiFiEvent(
    val event: AccountEkirjastoSuomiFiEvent
  ):LoginListenedEvent()

  data class LoginEvent(
    val event: org.librarysimplified.ui.login.LoginEvent
  ) : LoginListenedEvent()
  data class AccountDetailEvent(
    val event: org.nypl.simplified.ui.accounts.AccountDetailEvent
  ) : LoginListenedEvent()
}
