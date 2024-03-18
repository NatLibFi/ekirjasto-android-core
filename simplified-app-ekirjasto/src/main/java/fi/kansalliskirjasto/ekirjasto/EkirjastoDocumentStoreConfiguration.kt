package fi.kansalliskirjasto.ekirjasto

import org.librarysimplified.documents.DocumentConfiguration
import org.librarysimplified.documents.DocumentConfigurationServiceType
import java.net.URI

/* Note that we are doing a "translation hack" where we reuse these link slots instead of changing
 * the original code. This solution assumes that all the strings will be translated from the
 * "base" language into English, Finnish and Swedish later. So while the base version will say
 * "About" the English version will say "Feedback".
 *
 * A more robust solution might be to add overrides where the necessary fields are added and
 * properly named, but deadline pressures being what they are this will have to suffice.
 *
 * All our current link sites contain a separate language selector. Later when we get the user
 * language from somewhere we could include the language in the link as a URL param or something.
 * Alternatively if the forms themselves are embedded in the app then we should be already be able
 * to get the language from somewhere (which isn't possible as of this writing).
 *
 * The "real" names for the fields have been listed above the fields so that if you are trying to
 * find these fields in the future you might have more success.
 */
class EkirjastoDocumentStoreConfiguration : DocumentConfigurationServiceType {
  override val privacyPolicy: DocumentConfiguration? =
  DocumentConfiguration(
    name = null,
    remoteURI = URI.create("https://www.kansalliskirjasto.fi/fi/e-kirjasto/e-kirjaston-tietosuoja-ja-rekisteriseloste")
  )

  // If you are adding or trying to modify a feedback field, please have a look at the lines below
  // as well as the class documentation.
  val feedback: DocumentConfiguration? = null
  // A better name for this would be "feedback".
  // As to why this is weirdly named, check the long explanation given in the class documentation.
  override val about: DocumentConfiguration? =
  DocumentConfiguration(
    name = null,
    remoteURI = URI.create("https://lib.e-kirjasto.fi/palaute")
  )

  override val acknowledgements: DocumentConfiguration? =
    null

  // If you are adding or trying to modify an accessibilityStatement field be sure to have a look
  // at the lines below as well as the class documentation.
  val accessibilityStatement: DocumentConfiguration? = null
  // A better name for this would be "accessibilityStatement" or "accessibilityReport".
  // As to why this is weirdly named, check the long explanation given in the class documentation.
  override val eula: DocumentConfiguration? =
    DocumentConfiguration(
      name = null,
      remoteURI = URI.create("https://www.kansalliskirjasto.fi/fi/e-kirjasto/e-kirjaston-saavutettavuusseloste")
    )

  override val licenses: DocumentConfiguration? =
    null

  override val faq: DocumentConfiguration? =
    null
}
