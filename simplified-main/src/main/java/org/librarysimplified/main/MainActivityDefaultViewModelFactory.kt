package org.librarysimplified.main

import androidx.lifecycle.ViewModelProvider
import org.librarysimplified.ui.login.MainLoginEvent
import org.librarysimplified.ui.onboarding.OnboardingEvent
import org.librarysimplified.ui.splash.SplashEvent
import org.librarysimplified.ui.tutorial.TutorialEvent
import org.nypl.simplified.listeners.api.ListenerRepository
import org.nypl.simplified.listeners.api.ListenerRepositoryFactory
import org.nypl.simplified.ui.accounts.ekirjasto.TextSizeEvent

class MainActivityDefaultViewModelFactory(fallbackFactory: ViewModelProvider.Factory) :
  ListenerRepositoryFactory<MainActivityListenedEvent, Unit>(fallbackFactory) {

  override val initialState: Unit = Unit

  override fun onListenerRepositoryCreated(repository: ListenerRepository<MainActivityListenedEvent, Unit>) {
    repository.registerListener(SplashEvent::class, MainActivityListenedEvent::SplashEvent)
    repository.registerListener(OnboardingEvent::class, MainActivityListenedEvent::OnboardingEvent)
    repository.registerListener(TutorialEvent::class, MainActivityListenedEvent::TutorialEvent)
    repository.registerListener(TextSizeEvent::class, MainActivityListenedEvent::TextSizeEvent)
    repository.registerListener(MainLoginEvent::class, MainActivityListenedEvent::LoginEvent)
  }
}
