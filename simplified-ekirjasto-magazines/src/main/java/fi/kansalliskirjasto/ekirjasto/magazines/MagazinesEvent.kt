package fi.kansalliskirjasto.ekirjasto.magazines

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.ui.errorpage.ErrorPageParameters


/**
 * Events for the magazine browser and reader.
 */
sealed class MagazinesEvent {
  object GoUpwards : MagazinesEvent()

  data class OpenErrorPage(
    val parameters: ErrorPageParameters
  ) : MagazinesEvent()

  object LoginRequired: MagazinesEvent()
}
