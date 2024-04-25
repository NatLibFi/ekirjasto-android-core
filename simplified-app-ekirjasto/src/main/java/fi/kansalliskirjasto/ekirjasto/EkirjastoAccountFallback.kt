package fi.kansalliskirjasto.ekirjasto

import org.joda.time.DateTime
import org.nypl.simplified.accounts.api.AccountProvider
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.accounts.api.AccountProviderFallbackType
import org.nypl.simplified.accounts.api.AccountProviderType
import java.net.URI

/**
 * The fallback account for SimplyE: The classics collection.
 */

class EkirjastoAccountFallback : AccountProviderFallbackType {
  //private val authentication = AccountProviderAuthenticationDescription.Ekirjasto(
    
  //)
  /*  AccountProviderAuthenticationDescription.Basic(
      description = "Library Barcode",
      barcodeFormat = null,
      keyboard = AccountProviderAuthenticationDescription.KeyboardInput.DEFAULT,
      passwordKeyboard = AccountProviderAuthenticationDescription.KeyboardInput.DEFAULT,
      passwordMaximumLength = -1,
      labels = mapOf(),
      logoURI = URI.create("https://circulation.openebooks.us/images/FirstBookLoginButton280.png")
    )*/

  companion object {
    const val circulationAPIURL = BuildConfig.CIRCULATION_API_URL

    // This must be same as the id of the library on circulation backend.
    private val libraryProviderId = BuildConfig.LIBRARY_PROVIDER_ID
  }
  // This will be replaced by the remote account provider with same id.
  override fun get(): AccountProviderType {
    return AccountProvider(
      addAutomatically = true,
      authenticationDocumentURI = null,
      authentication = AccountProviderAuthenticationDescription.Anonymous,
      authenticationAlternatives = listOf(),
      cardCreatorURI = null,
      catalogURI = URI.create("$circulationAPIURL/healthcheck.html"),
      description = null,
      displayName = "E-Kirjasto Fallback Account",
      eula = null,
      id = URI.create("defaultProvider:$libraryProviderId"),
      idNumeric = -1,
      isProduction = true,
      license = null,
      loansURI = null,
      logo = null,
      mainColor = "orange",
      patronSettingsURI = null,
      privacyPolicy = null,
      subtitle = null,
      supportEmail = null,
      supportsReservations = true,
      updated = DateTime.parse("2019-07-08T16:32:52+00:00"),
      location = null,
      alternateURI = null,
      resetPasswordURI = null
    )
  }
}
