package org.nypl.simplified.patron.api

import org.nypl.simplified.links.Link
import java.net.URI

/**
 * Patron user profile.
 *
 * @see "https://github.com/NYPL-Simplified/Simplified/wiki/User-Profile-Management-Protocol"
 */

data class PatronUserProfile(
  val settings: PatronSettings,
  val links: List<Link>,
  val drm: List<PatronDRM>,
  val authorization: PatronAuthorization?
) {

  /**
   * The annotations link, if one was present.
   */

  private val annotationsLink: Link? =
    this.links.find { link -> link.relation == "http://www.w3.org/ns/oa#annotationService" }

  /**
   * The annotations URI, if one was present.
   */

  val annotationsURI: URI? =
    this.annotationsLink?.hrefURI

  /**
   * The device registration link, if one was present.
   */

  private val deviceRegistrationLink: Link? =
    this.links.find { link -> link.relation == "http://palaceproject.io/terms/deviceRegistration" }

  /**
   * The device registration URI, if one was present.
   */

  val deviceRegistrationURI: URI? =
    this.deviceRegistrationLink?.hrefURI
}
