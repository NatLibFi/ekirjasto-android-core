package org.nypl.simplified.ui.accounts.ekirjasto.suomifi

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import java.io.Serializable

/**
 * Parameters for the E-kirjasto fragment.
 */

data class AccountEkirjastoSuomiFiFragmentParameters(
  val accountID: AccountID,
  val authenticationDescription: AccountProviderAuthenticationDescription.Ekirjasto
) : Serializable
