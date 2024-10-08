package org.nypl.simplified.ui.accounts

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.ui.accounts.ekirjasto.suomifi.EkirjastoLoginMethod
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import java.net.URL

sealed class AccountDetailEvent {

  /**
   * The patron has successfully logged into the account.
   */

  object LoginSucceeded : AccountDetailEvent()

  /**
   * The patron is tired of looking at the account details.
   */

  object GoUpwards : AccountDetailEvent()

  /**
   * The patron wants to see the preference options.
   */
  object OpenPreferences : AccountDetailEvent()

  data class OpenWebView(val parameters: AccountCardCreatorParameters) : AccountDetailEvent()

  /**
   * The patron wants to log in through SAML.
   */

  data class OpenSAML20Login(
    val account: AccountID,
    val authenticationDescription: AccountProviderAuthenticationDescription.SAML2_0
  ) : AccountDetailEvent()

  /**
   * The patron wants to log in through E-kirjasto.
   */

  data class OpenEkirjastoSuomiFiLogin(
    val account: AccountID,
    val authenticationDescription: AccountProviderAuthenticationDescription.Ekirjasto,
    val loginMethod: EkirjastoLoginMethod.SuomiFi,
//    val username:String?,
  ) : AccountDetailEvent()

  data class OpenEkirjastoPasskeyLogin(
    val account: AccountID,
    val authenticationDescription: AccountProviderAuthenticationDescription.Ekirjasto,
    val loginMethod: EkirjastoLoginMethod.Passkey
//    val username:String?,
//    val ekirjastoToken: String?
  ) : AccountDetailEvent()

  /**
   * Login has failed and the patron wants to see some details about the error.
   */

  data class OpenErrorPage(
    val parameters: ErrorPageParameters
  ) : AccountDetailEvent()

  /**
   * Open the documentation viewer.
   */
  data class OpenDocViewer(
    val title: String,
    val url: URL
  ) : AccountDetailEvent()

  /**
   * Open the dependents view.
   */
  object OpenDependentInvite : AccountDetailEvent()
}
