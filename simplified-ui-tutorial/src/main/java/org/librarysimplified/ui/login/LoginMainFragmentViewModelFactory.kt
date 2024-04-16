package org.librarysimplified.ui.login

import androidx.lifecycle.ViewModelProvider
import org.nypl.simplified.listeners.api.ListenerRepository
import org.nypl.simplified.listeners.api.ListenerRepositoryFactory
import org.nypl.simplified.ui.accounts.AccountDetailEvent
import org.nypl.simplified.ui.accounts.ekirjasto.suomifi.AccountEkirjastoSuomiFiEvent

class LoginMainFragmentViewModelFactory(fallbackFactory: ViewModelProvider.Factory) :
  ListenerRepositoryFactory<LoginListenedEvent, Unit>(fallbackFactory) {

  override val initialState: Unit = Unit

  override fun onListenerRepositoryCreated(repository: ListenerRepository<LoginListenedEvent, Unit>) {
    repository.registerListener(LoginEvent::class, LoginListenedEvent::LoginEvent)
    repository.registerListener(AccountDetailEvent::class, LoginListenedEvent::AccountDetailEvent )
    repository.registerListener(AccountEkirjastoSuomiFiEvent::class, LoginListenedEvent::SuomiFiEvent)
  }
}
