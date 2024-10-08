package org.librarysimplified.main

sealed class MainFragmentListenedEvent {

  data class CatalogSAML20Event(
    val event: org.librarysimplified.ui.catalog.saml20.CatalogSAML20Event
  ) : MainFragmentListenedEvent()

  data class CatalogFeedEvent(
    val event: org.librarysimplified.ui.catalog.CatalogFeedEvent
  ) : MainFragmentListenedEvent()

  data class CatalogBookDetailEvent(
    val event: org.librarysimplified.ui.catalog.CatalogBookDetailEvent
  ) : MainFragmentListenedEvent()

  data class AccountSAML20Event(
    val event: org.nypl.simplified.ui.accounts.saml20.AccountSAML20Event
  ) : MainFragmentListenedEvent()

  data class AccountEkirjastoSuomiFiEvent(
    val event: org.nypl.simplified.ui.accounts.ekirjasto.suomifi.AccountEkirjastoSuomiFiEvent
  ) : MainFragmentListenedEvent()

  data class AccountDetailEvent(
    val event: org.nypl.simplified.ui.accounts.AccountDetailEvent
  ) : MainFragmentListenedEvent()

  data class PreferencesEvent(
    val event: org.nypl.simplified.ui.accounts.ekirjasto.PreferencesEvent
  ) : MainFragmentListenedEvent()

  data class AccountListEvent(
    val event: org.nypl.simplified.ui.accounts.AccountListEvent
  ) : MainFragmentListenedEvent()

  data class AccountListRegistryEvent(
    val event: org.nypl.simplified.ui.accounts.AccountListRegistryEvent
  ) : MainFragmentListenedEvent()

  data class AccountPickerEvent(
    val event: org.nypl.simplified.ui.accounts.AccountPickerEvent
  ) : MainFragmentListenedEvent()

  data class MagazinesEvent(
    val event: fi.kansalliskirjasto.ekirjasto.magazines.MagazinesEvent
  ) : MainFragmentListenedEvent()

  data class ErrorPageEvent(
    val event: org.nypl.simplified.ui.errorpage.ErrorPageEvent
  ) : MainFragmentListenedEvent()

  data class SettingsMainEvent(
    val event: org.nypl.simplified.ui.settings.SettingsMainEvent
  ) : MainFragmentListenedEvent()

  data class SettingsDebugEvent(
    val event: org.nypl.simplified.ui.settings.SettingsDebugEvent
  ) : MainFragmentListenedEvent()

  data class SettingsDocumentViewerEvent(
    val event: org.nypl.simplified.ui.settings.SettingsDocumentViewerEvent
  ) : MainFragmentListenedEvent()
}
