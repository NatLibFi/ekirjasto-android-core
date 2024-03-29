package org.nypl.simplified.accounts.source.ekirjasto

import android.content.Context
import org.librarysimplified.http.api.LSHTTPClientType
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionParsers
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionSerializers
import org.nypl.simplified.accounts.source.spi.AccountProviderSourceFactoryType
import org.nypl.simplified.accounts.source.spi.AccountProviderSourceType
import org.nypl.simplified.buildconfig.api.BuildConfigurationAccountsType
import org.nypl.simplified.opds.auth_document.api.AuthenticationDocumentParsersType
import org.nypl.simplified.opds2.irradia.OPDS2ParsersIrradia
import java.util.ServiceLoader

/**
 * A factory for E-kirjasto library registry sources.
 */

class AccountProviderSourceEkirjastoFactory : AccountProviderSourceFactoryType {

  private fun findAuthenticationDocumentParsers(): AuthenticationDocumentParsersType {
    return ServiceLoader.load(AuthenticationDocumentParsersType::class.java)
      .firstOrNull()
      ?: throw IllegalStateException("No available implementation of type ${AuthenticationDocumentParsersType::class.java}")
  }

  override fun create(
    context: Context,
    http: LSHTTPClientType,
    buildConfig: BuildConfigurationAccountsType
  ): AccountProviderSourceType {
    return AccountProviderSourceEkirjastoRegistry(
      http = http,
      authDocumentParsers = this.findAuthenticationDocumentParsers(),
      parsers = AccountProviderDescriptionCollectionParsers(OPDS2ParsersIrradia),
      serializers = AccountProviderDescriptionCollectionSerializers(),
      uriProduction = buildConfig.libraryRegistry.registry,
      uriQA = buildConfig.libraryRegistry.registryQA
    )
  }
}
