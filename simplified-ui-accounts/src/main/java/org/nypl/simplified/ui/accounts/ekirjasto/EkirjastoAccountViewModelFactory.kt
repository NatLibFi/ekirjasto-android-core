package org.nypl.simplified.ui.accounts.ekirjasto

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.ui.accounts.AccountDetailEvent

class EkirjastoAccountViewModelFactory(
  private val account: AccountID,
  private val listener: FragmentListenerType<AccountDetailEvent>,
  private val application: Application,
) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass == EkirjastoAccountViewModel::class.java) {
      return EkirjastoAccountViewModel(
        accountId = account,
        listener = listener,
        application = application,
      ) as T
    }
    throw IllegalStateException("Can't create values of $modelClass")
  }
}
