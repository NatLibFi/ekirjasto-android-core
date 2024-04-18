package org.librarysimplified.ui.login

sealed class LoginEvent {
  object StartLoginSuomiFi : LoginEvent()
  object StartLoginPasskey : LoginEvent()
  object SkipLogin : LoginEvent()
}
