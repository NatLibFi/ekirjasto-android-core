package fi.kansalliskirjasto.ekirjasto.magazines

import android.content.res.Resources
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.io7m.junreachable.UnreachableCodeException
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.PublishSubject
import net.jcip.annotations.GuardedBy
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.accounts.api.AccountEventCreation
import org.nypl.simplified.accounts.api.AccountEventDeletion
import org.nypl.simplified.accounts.api.AccountEventLoginStateChanged
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountLoginState
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.analytics.api.AnalyticsType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.futures.FluentFutureExtensions.map
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.thread.api.UIExecutor
import org.slf4j.LoggerFactory
import java.util.UUID


/**
 * A view model for the digital magazines fragment.
 */
class MagazinesViewModel(
  private val resources: Resources,
  private val magazinesHttp: MagazinesHttp,
  private val accountProviders: AccountProviderRegistryType,
  private val profilesController: ProfilesControllerType,
  private val buildConfiguration: BuildConfigurationServiceType,
  private val analytics: AnalyticsType,
  private val arguments: MagazinesArguments,
  private val listener: FragmentListenerType<MagazinesEvent>
) : ViewModel() {
  private val instanceId = UUID.randomUUID()

  private val logger = LoggerFactory.getLogger(this.javaClass)

  private val uiExecutor = UIExecutor()

  // Initial UI state is loading
  private val stateMutable: MutableLiveData<MagazinesState> =
    MutableLiveData(MagazinesState.MagazinesLoading(arguments))

  private data class MagazinesHttpResultWithArguments(
    val arguments: MagazinesArguments,
    val result: MagazinesHttpResult
  )

  val stateLive: LiveData<MagazinesState>
    get() = stateMutable

  private val state: MagazinesState
    get() = stateLive.value!!
  val token: String?
    get() = state.arguments.token

  @GuardedBy("magazinesHttpResults")
  private val magazinesHttpResults =
    PublishSubject.create<MagazinesHttpResultWithArguments>()

  private val subscriptions =
    CompositeDisposable(
      profilesController.accountEvents()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(this::onAccountEvent),
      magazinesHttpResults
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(this::onMagazinesHttpResult)
    )

  fun goUpwards() {
    listener.post(MagazinesEvent.GoUpwards)
  }

  fun getMagazineServiceUrl(): String? {
    logger.debug("getMagazineServiceUrl()")
    val accountId = getDefaultAccount().id
    val profile = profilesController.profileCurrent()
    val account = profile.account(accountId)
    val ekirjastoAuthDescription =
      account.provider.authentication as? AccountProviderAuthenticationDescription.Ekirjasto
    val magazineServiceUrl = ekirjastoAuthDescription?.magazine_service
    logger.debug("magazineServiceUrl: {}", magazineServiceUrl)
    return magazineServiceUrl?.toString()?.removeSuffix("/")
  }

  fun fetchTokenAsync() {
    logger.debug("fetchTokenAsync()")

    val accountId = getDefaultAccount().id
    val profile = profilesController.profileCurrent()
    val account = profile.account(accountId)
    val ekirjastoAuthDescription =
      account.provider.authentication as? AccountProviderAuthenticationDescription.Ekirjasto
    val ekirjastoTokenUrl = ekirjastoAuthDescription?.ekirjasto_token
    val credentials = account.loginState.credentials as? AccountAuthenticationCredentials.Ekirjasto
    val circulationToken = credentials?.accessToken

    if (ekirjastoTokenUrl == null) {
      logger.debug("ekirjastoTokenUrl is null, cannot fetch token for magazine service")
      // Sign in required
      stateMutable.value = MagazinesState.MagazinesLoadFailed(arguments)
      return
    }
    else if (circulationToken == null) {
      logger.debug("circulationToken is null, cannot fetch token for magazine service")
      // Sign in required
      stateMutable.value = MagazinesState.MagazinesLoadFailed(arguments)
      return
    }

    val future = magazinesHttp.fetchURI(
      uri = ekirjastoTokenUrl,
      circulationToken = circulationToken
    )
    future.map { magazinesHttpResult ->
      logger.debug("magazinesHttpResult: {}", magazinesHttpResult)

      synchronized(magazinesHttpResults) {
        val resultWithArguments = MagazinesHttpResultWithArguments(arguments, magazinesHttpResult)
        magazinesHttpResults.onNext(resultWithArguments)
      }
    }
  }

  private fun onMagazinesHttpResult(resultWithArguments: MagazinesHttpResultWithArguments) {
    logger.debug("onMagazinesHttpResult: {}", resultWithArguments)
    val result: MagazinesHttpResult = resultWithArguments.result
    val newArguments: MagazinesArguments = resultWithArguments.arguments
    when (result) {
      is MagazinesHttpResult.MagazinesHttpSuccess -> {
        logger.debug("got MagazinesHttpSuccess: {}", result.body)
        onTokenResult(result.body.get("token").asText())
      }
      is MagazinesHttpResult.MagazinesHttpFailure -> {
        logger.debug("got MagazinesHttpFailure: {}", result.message)
        onTokenResult(null)
      }
    }
  }

  private fun onTokenResult(token: String?) {
    val newArguments = MagazinesArguments.MagazinesArgumentsData(
      token = token,
    )

    if (token == null) {
      stateMutable.value = MagazinesState.MagazinesLoadFailed(newArguments)
    }
    else {
      stateMutable.value = MagazinesState.MagazinesBrowsing(newArguments)
    }
  }

  private fun onAccountEvent(event: AccountEvent) {
    when (event) {
      is AccountEventCreation.AccountEventCreationSucceeded,
      is AccountEventDeletion.AccountEventDeletionSucceeded -> {
        logger.debug("AccountEvent(Creation|Deletion)Succeeded")
      }
      is AccountEventLoginStateChanged -> {
        logger.debug("AccountEventLoginStateChanged: {}", event)
        onLoginStateChanged(event.accountID, event.state)
      }
    }
  }

  private fun onLoginStateChanged(accountId: AccountID, accountState: AccountLoginState) {
    logger.debug("onLoginStateChanged({}, {})", accountId, accountState)
    if (accountState is AccountLoginState.AccountLoggedIn) {
      logger.debug("AccountLoggedIn")
      // Set state to loading
      // TODO: Clear token here? (in case fetching fails)
      stateMutable.value = MagazinesState.MagazinesLoading(arguments)
      // Start fetching token
      fetchTokenAsync()
    }
    else if (accountState is AccountLoginState.AccountNotLoggedIn) {
      logger.debug("AccountNotLoggedIn")
      // TODO: Clear token here?
      stateMutable.value = MagazinesState.MagazinesLoadFailed(arguments)
    }
  }

  override fun onCleared() {
    super.onCleared()
    logger.debug("[{}]: deleting viewmodel", instanceId)
    subscriptions.clear()
    uiExecutor.dispose()
  }

  fun syncAccounts() {
    syncAccounts(state.arguments)
  }

  private fun syncAccounts(arguments: MagazinesArguments) {
    logger.debug("syncAccounts()")
  }

  private fun getDefaultAccount(): AccountType {
    val profile = profilesController.profileCurrent()
    val mostRecentId = profile.preferences().mostRecentAccount

    try {
      return profile.account(mostRecentId)
    }
    catch (e: Exception) {
      logger.error("stale account: ", e)
    }

    val accounts = profile.accounts().values
    return when {
      accounts.size > 1 -> {
        // Return the first account created from a non-default provider
        accounts.first { it.provider.id != accountProviders.defaultProvider.id }
      }
      accounts.size == 1 -> {
        // Return the first account
        accounts.first()
      }
      else -> {
        // There should always be at least one account
        throw UnreachableCodeException()
      }
    }
  }
}
