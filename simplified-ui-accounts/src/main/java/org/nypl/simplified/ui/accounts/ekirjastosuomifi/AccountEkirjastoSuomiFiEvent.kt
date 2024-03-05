package org.nypl.simplified.ui.accounts.ekirjastosuomifi

import org.nypl.simplified.ui.errorpage.ErrorPageParameters

sealed class AccountEkirjastoSuomiFiEvent {

  /**
   * The patron has successfully logged into the account.
   */

  object AccessTokenObtained : AccountEkirjastoSuomiFiEvent()
  object PasskeySuccessful : AccountEkirjastoSuomiFiEvent()

  /**
   * Login has failed and the patron wants to see some details about the error.
   */

  data class OpenErrorPage(
    val parameters: ErrorPageParameters
  ) : AccountEkirjastoSuomiFiEvent()
}
