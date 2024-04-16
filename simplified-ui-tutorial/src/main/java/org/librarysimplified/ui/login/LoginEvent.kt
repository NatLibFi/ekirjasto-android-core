package org.librarysimplified.ui.login

sealed interface LoginEvent {
  object StartLoginSuomiFi : LoginEvent
  object StartLoginPasskey : LoginEvent
  object SkipLogin : LoginEvent
}
