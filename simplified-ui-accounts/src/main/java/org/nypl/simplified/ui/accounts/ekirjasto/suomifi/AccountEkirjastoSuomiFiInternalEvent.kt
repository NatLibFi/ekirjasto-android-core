package org.nypl.simplified.ui.accounts.ekirjasto.suomifi

import org.nypl.simplified.accounts.api.AccountCookie

/**
 * Events raised during the E-kirjasto login process.
 */

sealed class AccountEkirjastoSuomiFiInternalEvent {

  /**
   * The web view client is ready for use. The login page should not be loaded until this event has
   * fired.
   */

  class WebViewClientReady() : AccountEkirjastoSuomiFiInternalEvent()


  /**
   * User cancelled the process.
   */

  class Cancel() : AccountEkirjastoSuomiFiInternalEvent()

  /**
   * The process failed.
   */

  data class Failed(
    val message: String
  ) : AccountEkirjastoSuomiFiInternalEvent()

  /**
   * An access token was obtained.
   */

  class AccessTokenStartReceive() : AccountEkirjastoSuomiFiInternalEvent()

  data class AccessTokenObtained(
    val token: String,
    val cookies: List<AccountCookie>
  ) : AccountEkirjastoSuomiFiInternalEvent()
}
