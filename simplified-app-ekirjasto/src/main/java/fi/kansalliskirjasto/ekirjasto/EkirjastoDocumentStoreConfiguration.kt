package fi.kansalliskirjasto.ekirjasto

import org.librarysimplified.documents.DocumentConfiguration
import org.librarysimplified.documents.DocumentConfigurationServiceType
import java.net.URI

class EkirjastoDocumentStoreConfiguration : DocumentConfigurationServiceType {

  override val privacyPolicy: DocumentConfiguration? =
    DocumentConfiguration(
      name = null,
      remoteURI = URI.create("https://www.kansalliskirjasto.fi/fi/e-kirjasto/e-kirjaston-tietosuoja-ja-rekisteriseloste")
    )

  override val feedback: DocumentConfiguration? =
    DocumentConfiguration(
      name = null,
      remoteURI = URI.create("https://lib.e-kirjasto.fi/palaute")
    )

  override val accessibilityStatement: DocumentConfiguration? =
    DocumentConfiguration(
      name = null,
      remoteURI = URI.create("https://www.kansalliskirjasto.fi/fi/e-kirjasto/e-kirjaston-saavutettavuusseloste")
    )

  override val about: DocumentConfiguration? =
    null

  override val acknowledgements: DocumentConfiguration? =
    null

  override val eula: DocumentConfiguration? =
    DocumentConfiguration(
      name = null,
      remoteURI = URI.create("https://www.kansalliskirjasto.fi/fi/e-kirjasto/e-kirjaston-kayttoehdot")
    )

  override val licenses: DocumentConfiguration? =
    null

  override val faq: DocumentConfiguration? =
    null
}
