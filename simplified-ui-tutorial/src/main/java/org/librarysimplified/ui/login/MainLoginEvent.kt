package org.librarysimplified.ui.login

sealed class MainLoginEvent{
  object SkipLoginEvent : MainLoginEvent()
  object LoginSuccess : MainLoginEvent()
}
