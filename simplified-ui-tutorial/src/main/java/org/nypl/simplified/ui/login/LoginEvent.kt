package org.nypl.simplified.ui.login

sealed interface LoginEvent {
  object StartLogin : LoginEvent
}
