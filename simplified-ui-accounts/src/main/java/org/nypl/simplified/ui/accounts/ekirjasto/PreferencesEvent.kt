package org.nypl.simplified.ui.accounts.ekirjasto

sealed class PreferencesEvent {

  //User wants out of preferences into settings
  object GoUpwards : PreferencesEvent()

}
