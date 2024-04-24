package fi.kansalliskirjasto.ekirjasto.magazines

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.librarysimplified.services.api.ServiceDirectoryType
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.analytics.api.AnalyticsType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.slf4j.LoggerFactory


/**
 * Factory for MagazinesViewModel.
 */
class MagazinesViewModelFactory(
  private val application: Application,
  private val services: ServiceDirectoryType,
  private val arguments: MagazinesArguments,
  private val listener: FragmentListenerType<MagazinesEvent>
) : ViewModelProvider.Factory {
  private val logger = LoggerFactory.getLogger(MagazinesViewModelFactory::class.java)

  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    logger.debug("requested creation of view model of type {}", modelClass)

    return when {
      modelClass.isAssignableFrom(MagazinesViewModel::class.java) -> {
        val magazinesHttp: MagazinesHttp =
          services.requireService(MagazinesHttp::class.java)
        val accountProviders: AccountProviderRegistryType =
          services.requireService(AccountProviderRegistryType::class.java)
        val profilesController: ProfilesControllerType =
          services.requireService(ProfilesControllerType::class.java)
        val buildConfig: BuildConfigurationServiceType =
          services.requireService(BuildConfigurationServiceType::class.java)
        val analytics: AnalyticsType =
          services.requireService(AnalyticsType::class.java)

        MagazinesViewModel(
          application.resources,
          magazinesHttp,
          accountProviders,
          profilesController,
          buildConfig,
          analytics,
          arguments,
          listener
        ) as T
      }
      else -> {
        throw IllegalArgumentException(
          "This view model factory (${this.javaClass}) cannot produce view models of type $modelClass"
        )
      }
    }
  }
}
