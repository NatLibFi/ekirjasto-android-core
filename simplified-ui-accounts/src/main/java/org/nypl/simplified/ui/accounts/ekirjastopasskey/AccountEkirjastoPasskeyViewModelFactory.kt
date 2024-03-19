package org.nypl.simplified.ui.accounts.ekirjastopasskey

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.ui.accounts.ekirjastosuomifi.AccountEkirjastoSuomiFiViewModel

class AccountEkirjastoPasskeyViewModelFactory(
  private val application: Application,
  private val account: AccountID,
  private val description: AccountProviderAuthenticationDescription.Ekirjasto,
  private val ekirjastoToken: String?,
) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass == AccountEkirjastoPasskeyViewModel::class.java) {
      return AccountEkirjastoPasskeyViewModel(
        application = this.application,
        account = this.account,
        description = this.description,
        ekirjastoToken = this.ekirjastoToken
      ) as T
    }
    throw IllegalStateException("Can't create values of $modelClass")
  }
}
