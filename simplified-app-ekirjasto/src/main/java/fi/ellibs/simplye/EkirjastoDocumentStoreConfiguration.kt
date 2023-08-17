package fi.ellibs.simplye

import org.librarysimplified.documents.DocumentConfiguration
import org.librarysimplified.documents.DocumentConfigurationServiceType
import java.net.URI

class EkirjastoDocumentStoreConfiguration : DocumentConfigurationServiceType {

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
