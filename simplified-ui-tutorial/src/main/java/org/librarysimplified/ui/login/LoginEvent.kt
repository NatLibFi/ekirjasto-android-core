package org.librarysimplified.ui.login

sealed interface LoginEvent {
  object StartLogin : LoginEvent
}
