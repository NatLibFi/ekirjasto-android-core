package org.nypl.simplified.ui.accounts.ekirjasto.passkey

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.ui.accounts.ekirjasto.suomifi.EkirjastoLoginMethod
import java.io.Serializable

/**
 * Parameters for the E-kirjasto Passkey fragment.
 */

data class AccountEkirjastoPasskeyFragmentParameters(
  val accountID: AccountID,
  val authenticationDescription: AccountProviderAuthenticationDescription.Ekirjasto,
  val loginMethod : EkirjastoLoginMethod.Passkey
) : Serializable
