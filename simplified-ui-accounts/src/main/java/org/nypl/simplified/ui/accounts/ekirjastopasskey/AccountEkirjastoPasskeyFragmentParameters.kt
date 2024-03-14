package org.nypl.simplified.ui.accounts.ekirjastopasskey

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import java.io.Serializable

/**
 * Parameters for the E-kirjasto Passkey fragment.
 */

data class AccountEkirjastoPasskeyFragmentParameters(
  val accountID: AccountID,
  val authenticationDescription: AccountProviderAuthenticationDescription.Ekirjasto,
  val username: String,
  val ekirjastoToken: String?
) : Serializable
