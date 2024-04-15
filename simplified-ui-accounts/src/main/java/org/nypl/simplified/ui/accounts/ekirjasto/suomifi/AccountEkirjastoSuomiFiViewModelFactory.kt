package org.nypl.simplified.ui.accounts.ekirjasto.suomifi

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription

/**
 * A factory for E-kirjasto view state.
 */

class AccountEkirjastoSuomiFiViewModelFactory(
  private val application: Application,
  private val account: AccountID,
  private val description: AccountProviderAuthenticationDescription.Ekirjasto,
) : ViewModelProvider.Factory {

  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass == AccountEkirjastoSuomiFiViewModel::class.java) {
      return AccountEkirjastoSuomiFiViewModel(
        application = this.application,
        account = this.account,
        description = this.description
      ) as T
    }
    throw IllegalStateException("Can't create values of $modelClass")
  }
}
