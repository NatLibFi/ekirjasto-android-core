package fi.ellibs.simplye

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
  private val basicAuth =
    AccountProviderAuthenticationDescription.Basic(
      description = "Library Barcode",
      barcodeFormat = null,
      keyboard = AccountProviderAuthenticationDescription.KeyboardInput.DEFAULT,
      passwordKeyboard = AccountProviderAuthenticationDescription.KeyboardInput.DEFAULT,
      passwordMaximumLength = -1,
      labels = mapOf(),
      logoURI = URI.create("https://circulation.openebooks.us/images/FirstBookLoginButton280.png")
    )

  private val ellibsAccountProvider = AccountProvider(
      addAutomatically = true,
      authenticationDocumentURI = URI.create("https://circulation-beta.ellibs.com/ellibs-test/authentication_document"),
      authentication = this.basicAuth,
      authenticationAlternatives = listOf(),
      cardCreatorURI = null,
      catalogURI = URI.create("https://circulation-beta.ellibs.com/ellibs-test/groups/"),
      description = "Ellibs Test Collection of open ebooks",
      displayName = "Ellibs Test Account",
      eula = null,
      id = URI.create("urn:uuid:2a591c60-ff9d-11ed-be56-0242ac120002"),
      idNumeric = -1,
      isProduction = true,
      license = URI.create("http://www.librarysimplified.org/iclicenses.html"),
      loansURI = URI.create("https://circulation-beta.ellibs.com/ellibs-test/loans/"),
      logo = null,
      mainColor = "orange",
      patronSettingsURI = URI.create("https://circulation-beta.ellibs.com/ellibs-test/patrons/me/"),
      privacyPolicy = null,
      subtitle = "A selection of classics and modern material available to anyone, with no library card necessary.",
      supportEmail = "mailto:support@ellibs.com",
      supportsReservations = true,
      updated = DateTime.parse("2019-07-08T16:32:52+00:00"),
      location = null,
      alternateURI = null,
      resetPasswordURI = null
    )

  private val openAccessAccountProvider = ellibsAccountProvider.copy(
      authenticationDocumentURI = URI.create("https://circulation-beta.ellibs.com/op-library/authentication_document"),
      catalogURI = URI.create("https://circulation-beta.ellibs.com/op-library/groups/"),
      loansURI = URI.create("https://circulation-beta.ellibs.com/op-library/loans/"),
      patronSettingsURI = URI.create("https://circulation-beta.ellibs.com/op-library/patrons/me/"),
    )

  override fun get(): AccountProviderType {
    return ellibsAccountProvider
//    return AccountProvider(
//      addAutomatically = true,
//      // authenticationDocumentURI = URI.create("https://circulation-beta.ellibs.com/ellibs-test/authentication_document"),
//      authenticationDocumentURI = URI.create("https://circulation-beta.ellibs.com/op-library/authentication_document"),
//      authentication = this.basicAuth,
//      authenticationAlternatives = listOf(),
//      cardCreatorURI = null,
//      //catalogURI = URI.create("https://circulation-beta.ellibs.com/ellibs-test/groups/"),
//      catalogURI = URI.create("https://circulation-beta.ellibs.com/op-library/groups/"),
//      description = "Ellibs Test Collection of open ebooks",
//      displayName = "Ellibs Test",
//      eula = null,
//      id = URI.create("urn:uuid:2a591c60-ff9d-11ed-be56-0242ac120002"),
//      idNumeric = -1,
//      isProduction = true,
//      license = URI.create("http://www.librarysimplified.org/iclicenses.html"),
//      // loansURI = URI.create("https://circulation-beta.ellibs.com/ellibs-test/loans/"),
//      loansURI = URI.create("https://circulation-beta.ellibs.com/op-library/loans/"),
//      logo = URI.create("https://www.ellibs.com/themes/ellibs/img/ellibs_logo_orangetext_2x.png"),
//      mainColor = "orange",
//      // patronSettingsURI = URI.create("https://circulation-beta.ellibs.com/ellibs-test/patrons/me/"),
//      patronSettingsURI = URI.create("https://circulation-beta.ellibs.com/op-library/patrons/me/"),
//      privacyPolicy = null,
//      subtitle = "A selection of classics and modern material available to anyone, with no library card necessary.",
//      supportEmail = "mailto:support@ellibs.com",
//      supportsReservations = false,
//      updated = DateTime.parse("2019-07-08T16:32:52+00:00"),
//      location = null,
//      alternateURI = null,
//      resetPasswordURI = null
//    )
  }
}
