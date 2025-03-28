package org.librarysimplified.main

import org.librarysimplified.ui.login.MainLoginEvent

sealed class MainActivityListenedEvent {

  data class SplashEvent(
    val event: org.librarysimplified.ui.splash.SplashEvent
  ) : MainActivityListenedEvent()

  data class TutorialEvent(
    val event: org.librarysimplified.ui.tutorial.TutorialEvent
  ) : MainActivityListenedEvent()

  data class OnboardingEvent(
    val event: org.librarysimplified.ui.onboarding.OnboardingEvent
  ) : MainActivityListenedEvent()

  data class TextSizeEvent(
    val event: org.nypl.simplified.ui.accounts.ekirjasto.TextSizeEvent
  ) : MainActivityListenedEvent()

  data class TipsEvent (
    val event: org.nypl.simplified.ui.announcements.TipsEvent
  ): MainActivityListenedEvent()

  data class LoginEvent(
    val event: MainLoginEvent
  ) : MainActivityListenedEvent()
}
