package org.nypl.simplified.accounts.api

import net.jcip.annotations.ThreadSafe
import org.joda.time.DateTime
import org.nypl.simplified.announcements.Announcement
import org.nypl.simplified.links.Link
import org.nypl.simplified.opds.core.OPDSFeedConstants.AUTHENTICATION_DOCUMENT_RELATION_URI_TEXT
import java.net.URI

/**
 * A provider of accounts.
 *
 * Implementations are required to be safe to manipulate from multiple threads.
 */

@ThreadSafe
interface AccountProviderType : Comparable<AccountProviderType> {

  /**
   * @return The account provider URI
   */

  val id: URI

  /**
   * @return The old-style numeric ID of the account
   */

  @Deprecated("Use URI-based IDs")
  val idNumeric: Int

  /**
   * @return `true` if this account is in production
   */

  val isProduction: Boolean

  /**
   * @return The display name
   */

  val displayName: String

  /**
   * @return The description
   */

  val description: String?

  /**
   * @return The subtitle
   */

  val subtitle: String?

  /**
   * @return The logo image
   */

  val logo: URI?

  /**
   * @return An authentication description
   */

  val authentication: AccountProviderAuthenticationDescription

  /**
   * @return A list of alternative authentication descriptions
   */

  val authenticationAlternatives: List<AccountProviderAuthenticationDescription>

  /**
   * @return `true` iff reservations are supported
   */

  val supportsReservations: Boolean

  /**
   * @return The URI of the user loans feed, if supported
   */

  val loansURI: URI?

  /**
   * @return The URI of the user selected feed, if supported
   */

  val selectedURI: URI?

  /**
   * @return The URI to reset the user's password, if any
   */

  val resetPasswordURI: URI?

  /**
   * @return The URI of the card creator iff card creation is supported
   */

  val cardCreatorURI: URI?

  /**
   * @return The address of the authentication document for the account provider
   */

  val authenticationDocumentURI: URI?

  /**
   * @return The base URI of the catalog
   */

  val catalogURI: URI

  /**
   * @return The support email address
   */

  val supportEmail: String?

  /**
   * @return The URI of the EULA if one is required
   */

  val eula: URI?

  /**
   * @return The URI of the EULA if one is required
   */

  val license: URI?

  /**
   * @return The URI of the privacy policy if one is required
   */

  val privacyPolicy: URI?

  /**
   * @return The main color used to decorate the application when using this provider
   */

  val mainColor: String

  /**
   * @return `true` iff the account should be added by default
   */

  val addAutomatically: Boolean

  /**
   * The patron settings URI. This is the URI used to get and set patron settings.
   *
   * @return The patron settings URI
   */

  val patronSettingsURI: URI?

  /**
   * An "alternate" URI; typically the address of a web site for the library in question.
   */

  val alternateURI: URI?

  /**
   * Determine the correct catalog URI to use for readers of a given age.
   *
   * @param age The age of the reader
   * @return The correct catalog URI for the given age
   */

  fun catalogURIForAge(age: Int): URI {
    return when (val auth = this.authentication) {
      is AccountProviderAuthenticationDescription.COPPAAgeGate ->
        if (age >= 13) {
          auth.greaterEqual13
        } else {
          auth.under13
        }

      is AccountProviderAuthenticationDescription.SAML2_0,
      AccountProviderAuthenticationDescription.Anonymous,
      is AccountProviderAuthenticationDescription.Basic,
      is AccountProviderAuthenticationDescription.BasicToken,
      is AccountProviderAuthenticationDescription.OAuthWithIntermediary,
      is AccountProviderAuthenticationDescription.Ekirjasto ->
        this.catalogURI
    }
  }

  /**
   * @return The time that this account provider was most recently updated
   */

  val updated: DateTime

  /**
   * @return `true` if the authentication settings imply that barcode scanning and display is supported
   */

  val supportsBarcodeDisplay: Boolean
    get() = when (val auth = this.authentication) {
      is AccountProviderAuthenticationDescription.SAML2_0,
      AccountProviderAuthenticationDescription.Anonymous,
      is AccountProviderAuthenticationDescription.OAuthWithIntermediary,
      is AccountProviderAuthenticationDescription.COPPAAgeGate,
      is AccountProviderAuthenticationDescription.Ekirjasto ->
        false
      is AccountProviderAuthenticationDescription.Basic -> {
        when (auth.barcodeFormat) {
          "Codabar" -> true
          else -> false
        }
      }
      is AccountProviderAuthenticationDescription.BasicToken -> {
        when (auth.barcodeFormat) {
          "Codabar" -> true
          else -> false
        }
      }
    }

  /**
   * @return A list of the most recently published announcements
   */

  val announcements: List<Announcement>

  /**
   * The location of the library, if any
   */

  val location: AccountLibraryLocation?

  fun toDescription(): AccountProviderDescription {
    val imageLinks = mutableListOf<Link>()
    this.logo?.let { uri ->
      imageLinks.add(
        Link.LinkBasic(
          href = uri,
          type = null,
          relation = "http://opds-spec.org/image/thumbnail"
        )
      )
    }

    val links = mutableListOf<Link>()
    addLink(links, this.catalogURI, "http://opds-spec.org/catalog")

    this.authenticationDocumentURI?.let { uri ->
      addLink(links, uri, AUTHENTICATION_DOCUMENT_RELATION_URI_TEXT)
    }
    this.cardCreatorURI?.let { uri ->
      addLink(links, uri, "register")
    }
    this.eula?.let { uri ->
      addLink(links, uri, "terms-of-service")
    }
    this.license?.let { uri ->
      addLink(links, uri, "license")
    }
    this.loansURI?.let { uri ->
      addLink(links, uri, "http://opds-spec.org/shelf")
    }
    this.patronSettingsURI?.let { uri ->
      addLink(links, uri, "http://librarysimplified.org/terms/rel/user-profile")
    }
    this.privacyPolicy?.let { uri ->
      addLink(links, uri, "privacy-policy")
    }

    val accountProviderDescription =
      AccountProviderDescription(
        id = id,
        title = displayName,
        description = description,
        updated = updated,
        links = links.toList(),
        images = imageLinks.toList(),
        isAutomatic = addAutomatically,
        isProduction = isProduction,
        location = location
      )

    check((this.authenticationDocumentURI != null) == (accountProviderDescription.authenticationDocumentURI != null))
    return accountProviderDescription
  }

  private fun addLink(links: MutableList<Link>, uri: URI, relation: String): Boolean {
    return links.add(
      Link.LinkBasic(
        href = uri,
        type = null,
        relation = relation
      )
    )
  }
}
