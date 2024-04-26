package fi.kansalliskirjasto.ekirjasto

import org.librarysimplified.main.BuildConfig
import org.nypl.simplified.buildconfig.api.BuildConfigOAuthScheme
import org.nypl.simplified.buildconfig.api.BuildConfigurationAccountsRegistryURIs
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import java.net.URI

class EkirjastoBuildConfigurationService : BuildConfigurationServiceType {
  override val libraryRegistry: BuildConfigurationAccountsRegistryURIs
    get() = BuildConfigurationAccountsRegistryURIs(
      registry = URI("${EkirjastoAccountFallback.circulationAPIURL}/libraries"),
      registryQA = URI("")
    )
  override val allowAccountsAccess: Boolean
    get() = true
  override val allowAccountsRegistryAccess: Boolean
    get() = true
  override val featuredLibrariesIdsList: List<String>
    get() = listOf()
  override val showDebugBookDetailStatus: Boolean
    get() = false
  override val showSettingsTab: Boolean
    get() = true
  override val showHoldsTab: Boolean
    get() = true
  override val showBooksFromAllAccounts: Boolean
    get() = false
  override val vcsCommit: String
    get() = BuildConfig.SIMPLIFIED_GIT_COMMIT
  override val simplifiedVersion: String
    get() = BuildConfig.SIMPLIFIED_VERSION
  override val supportErrorReportEmailAddress: String
    get() = "support@ellibs.com"
  override val supportErrorReportSubject: String
    get() = "[ekirjasto-error]"
  override val oauthCallbackScheme: BuildConfigOAuthScheme
    get() = BuildConfigOAuthScheme("ekirjasto")
  override val allowExternalReaderLinks: Boolean
    get() = false
  override val showChangeAccountsUi: Boolean
    get() = false
  override val showActionBarLogo: Boolean
    get() = true
  override val showAgeGateUi: Boolean
    get() = true
  override val brandingAppIcon: Int
    get() = R.drawable.main_icon
}
