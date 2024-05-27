package fi.kansalliskirjasto.ekirjasto

import org.librarysimplified.documents.DocumentConfiguration
import org.librarysimplified.documents.DocumentConfigurationServiceType
import java.net.URI
import android.os.Build

@Suppress("RedundantNullableReturnType")
class EkirjastoDocumentStoreConfiguration : DocumentConfigurationServiceType {

  override val privacyPolicy: DocumentConfiguration? =
    DocumentConfiguration(
      name = null,
      remoteURI = URI.create("https://www.kansalliskirjasto.fi/__LANGUAGE__/e-kirjasto/e-kirjaston-tietosuoja-ja-rekisteriseloste")
    )

  override val feedback: DocumentConfiguration? =
    DocumentConfiguration(
      name = null,
      remoteURI = URI.create("https://lib.e-kirjasto.fi/palaute/?lang=__LANGUAGE__&device_model=${Build.MANUFACTURER}%20${Build.MODEL}&software_version=${BuildConfig.VERSION_NAME}%20(${BuildConfig.VERSION_CODE})".replace(" ", "%20"))
    )

  override val accessibilityStatement: DocumentConfiguration? =
    DocumentConfiguration(
      name = null,
      remoteURI = URI.create("https://www.kansalliskirjasto.fi/__LANGUAGE__/e-kirjasto/e-kirjaston-saavutettavuusseloste")
    )

  override val about: DocumentConfiguration? =
    null

  override val acknowledgements: DocumentConfiguration? =
    null

  override val eula: DocumentConfiguration? =
    DocumentConfiguration(
      name = null,
      remoteURI = URI.create("https://www.kansalliskirjasto.fi/__LANGUAGE__/e-kirjasto/e-kirjaston-kayttoehdot")
    )

  override val licenses: DocumentConfiguration? =
    null

  override val faq: DocumentConfiguration? =
    DocumentConfiguration(
      name = null,
      remoteURI = URI.create("https://www.kansalliskirjasto.fi/__LANGUAGE__/e-kirjasto/e-kirjaston-usein-kysytyt-kysymykset")
    )
}
