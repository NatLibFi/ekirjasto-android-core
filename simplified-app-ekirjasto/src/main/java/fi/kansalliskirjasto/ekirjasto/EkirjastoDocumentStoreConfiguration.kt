package fi.kansalliskirjasto.ekirjasto

import org.librarysimplified.documents.DocumentConfiguration
import org.librarysimplified.documents.DocumentConfigurationServiceType
import java.net.URI

class EkirjastoDocumentStoreConfiguration : DocumentConfigurationServiceType {

  // TODO these hardcoded links should be softcoded instead which will be possible after support for
  // them is added to the backend.
  override val feedback: DocumentConfiguration? =
    DocumentConfiguration(
      name = "feedback.html",
      remoteURI = URI.create("https://forms.office.com/e/hYk8n3agXi")
    )

  override val privacyPolicy: DocumentConfiguration? =
    null

  override val about: DocumentConfiguration? =
    null

  override val acknowledgements: DocumentConfiguration? =
    null

  override val eula: DocumentConfiguration? =
    null

  override val licenses: DocumentConfiguration? =
    null

  override val faq: DocumentConfiguration? =
    null
}
