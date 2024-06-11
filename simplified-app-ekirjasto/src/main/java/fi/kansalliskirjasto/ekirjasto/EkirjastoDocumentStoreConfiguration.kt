package fi.kansalliskirjasto.ekirjasto

import android.os.Build
import java.net.URI
import java.net.URLEncoder
import org.librarysimplified.documents.DocumentConfiguration
import org.librarysimplified.documents.DocumentConfigurationServiceType
import org.librarysimplified.main.BuildConfig as MainBuildConfig

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
      remoteURI = URI.create(getFeedbackUrl())
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

  private fun getFeedbackUrl(): String {
    var url = BuildConfig.FEEDBACK_URL_BASE
    url += "?lang=__LANGUAGE__"
    url += "&device_manufacturer=${URLEncoder.encode(Build.MANUFACTURER, "UTF-8")}"
    url += "&device_model=${URLEncoder.encode(Build.MODEL, "UTF-8")}"
    url += "&version_name=${URLEncoder.encode(BuildConfig.VERSION_NAME, "UTF-8")}"
    url += "&version_code=${URLEncoder.encode(BuildConfig.VERSION_CODE.toString(), "UTF-8")}"
    url += "&commit=${URLEncoder.encode(MainBuildConfig.SIMPLIFIED_GIT_COMMIT, "UTF-8")}"
    return url
  }
}
