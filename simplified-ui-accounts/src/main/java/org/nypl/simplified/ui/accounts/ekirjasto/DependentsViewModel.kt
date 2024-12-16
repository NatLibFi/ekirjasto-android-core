package org.nypl.simplified.ui.accounts.ekirjasto

import androidx.annotation.GuardedBy
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.common.util.concurrent.AsyncFunction
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import one.irradia.mime.api.MIMEType
import org.librarysimplified.http.api.LSHTTPAuthorizationBearerToken
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPRequestBuilderType
import org.librarysimplified.http.api.LSHTTPRequestType
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.librarysimplified.services.api.Services
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.futures.FluentFutureExtensions.map
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.charset.Charset

class DependentsViewModel(
) : ViewModel() {

  //Get the services needed for functionalities
  private val services = Services.serviceDirectory()
  private val profiles = services.requireService(ProfilesControllerType::class.java)
  private val dependentsHttp= services.requireService(DependentsHttp::class.java)
  private val http = services.requireService(LSHTTPClientType::class.java)
  private val logger = LoggerFactory.getLogger(DependentsViewModel::class.java)
  private val steps: TaskRecorderType = TaskRecorder.create()

  //List of the dependents returned by the server
  private val dependentList = MutableLiveData<List<Dependent>>()

  //Observable dependent list
  val dependentListLive: LiveData<List<Dependent>>
    get() = dependentList

  //Create dependentsHttpResults we want to observe changes to
  @GuardedBy("dependentsHttpResults")
  private val dependentsHttpResults =
    PublishSubject.create<DependentsHttpResult>()

  //Observe the changes of the dependentsHTTPResults
  private val subscriptions =
    CompositeDisposable(
      dependentsHttpResults
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(this::onDependentsHttpResult)
    )

  //Dependent post URI
  private lateinit var dependentPostURI: URI

  //Mutable state of the dependents
  private val stateMutable: MutableLiveData<DependentsState> =
    MutableLiveData(DependentsState.DependentsLoading(null))

  //Observable state
  val stateLive: LiveData<DependentsState>
    get() = stateMutable

  //Actual state, returns statelive when asked by observers
  private val state: DependentsState
    get() = stateLive.value!!

  //The ekirjasto token currently stored in fragment state
  //Is ekirjasto token if successful token has been fetched
  //Otherwise null
  val token: String?
    get() = state.ekirjastoToken

  //Error message
  private val error =  MutableLiveData<String>()

  //Observable error message
  val errorLive: LiveData<String>
    get() = error

  /**
   * Get the default account. There is only one account we use, so it should always be found.
   */
  private fun getDefaultAccount(): AccountType? {
    steps.beginNewStep("Get current account")
    val profile = profiles.profileCurrent()
    val mostRecentId = profile.preferences().mostRecentAccount

    try {
      steps.currentStepSucceeded("Found account")
      return profile.account(mostRecentId)
    } catch (e: Exception) {
      logger.error("stale account: ", e)
    }
    return null
  }

  /**
   * Handle the ekirjastoToken lookup. Function makes a call to circulation to get ekirjasto token
   * and updates dependentsHTTPresult.
   */
  fun lookupTokenForDependents() {
    //Get account information
    //Get id of default account, that we are always on
    val accountId = getDefaultAccount()!!.id
    //We only have one profile, ekirjasto
    val profile = profiles.profileCurrent()
    //Get the matching account from ekirjasto
    val account = profile.account(accountId)
    //Credentials are of the type Ekirjasto always
    val credentials = account.loginState.credentials as? AccountAuthenticationCredentials.Ekirjasto
    //AuthDescription contains URIs used
    val ekirjastoAuthDescription =
      account.provider.authentication as? AccountProviderAuthenticationDescription.Ekirjasto
    steps.beginNewStep("Get token and patron info from profile")
    val circulationToken = credentials?.accessToken
    steps.currentStepSucceeded("Token and patron found in profile")
    steps.beginNewStep("Get the dependent and token URIs from description")
    val ekirjastoTokenUrl = ekirjastoAuthDescription?.ekirjasto_token
    this.dependentPostURI = ekirjastoAuthDescription?.invite!!
    steps.currentStepSucceeded("URIs found in description")

    //Get the ekirjastoToken from circulation
    steps.beginNewStep("Get Ekirjasto token")
    val future = dependentsHttp.fetchURI(
      ekirjastoTokenUrl!!,
      circulationToken!!,
      false
    )
    future.map { dependentsHttpResult ->
      logger.debug("dependentsHttpResult: {}", dependentsHttpResult)

      //Update the dependentsHttpResult that is observed with the returned one
      synchronized(dependentsHttpResult) {
        dependentsHttpResults.onNext(dependentsHttpResult)
      }
    }
  }

  /**
   * Observer for changes in the DependentsHttpResult. Takes action
   * based on which result is returned. Is responsible for triggering token refresh
   * if needed.
   */
  private fun onDependentsHttpResult(result: DependentsHttpResult) {
    logger.debug("run onDependentsHttpResult")
    when (result) {
      is DependentsHttpResult.DependentsHttpTokenSuccess -> {
        //ekirjastoToken was fetched successfully
        logger.debug("got MagazinesHttpSuccess: {}", result.ekirjastoToken)
        onTokenResult(result.ekirjastoToken)
      }
      is DependentsHttpResult.DependentsHttpTokenError -> {
        //If error is 401, we try to refresh the circulation token
        if (result.message == "accessToken refresh needed") {
          logger.debug("Try refreshing accessToken")
          val account = profiles.profileCurrent().mostRecentAccount()
          val authenticationDescription = account.provider.authentication as AccountProviderAuthenticationDescription.Ekirjasto
          val credentials = account.loginState.credentials as AccountAuthenticationCredentials.Ekirjasto
          val accessToken = credentials.accessToken

          //Launch accessToken refresh
          val refreshResult = profiles.profileAccountAccessTokenRefresh(
            ProfileAccountLoginRequest.EkirjastoAccessTokenRefresh(
              accountId = account.id,
              description = authenticationDescription,
              accessToken = accessToken
            )
          ).transformAsync(AsyncFunction { taskResult ->
            //If refresh successful, lookup ekirjastoToken again
            if(taskResult is TaskResult.Success) {
              this.lookupTokenForDependents()
            }
            //Otherwise just return the result
            Futures.immediateFuture(taskResult)
          }, MoreExecutors.directExecutor())
        } else {
          //On other results errors in token lookup, token result is set to null
          logger.debug("got DependentsHttpError: {}", result.message)
          //Inform fragment about error
          error.postValue("Error")
          onTokenResult(null)
        }
      }
      is DependentsHttpResult.DependentsHttpTokenFailure -> {
        //Inform the fragment to show the failure message
        error.postValue("Failure")
        //Set the token as failure
        onTokenResult("Failure")
      }
      is DependentsHttpResult.DependentsHttpDependentLookupSuccess -> {
        //create an array node from the values listed as items
        steps.beginNewStep("Update dependents list started")
        //Update the observable dependents list
        dependentList.postValue(result.dependents)
        steps.currentStepSucceeded("Dependents list updated")
      }
      is DependentsHttpResult.DependentsHttpDependentLookupFailure -> {
        //Handle failure by logging and informing user
        logger.warn(result.message)
        error.postValue("Failure")
        onTokenResult("Failure")
      }

      is DependentsHttpResult.DependentsHttpDependentLookupError -> {
        //Handle error by logging and informing user
        logger.warn(result.message)
        error.postValue("Error")
        onTokenResult("Error")
      }

    }
  }

  /**
   * Update the state based on the response.
   */
  private fun onTokenResult(token: String?) {
    if (token == null) {
      stateMutable.value = DependentsState.EkirjastoTokenLoadFailed(null)
    }
    if (token == "refresh") {
      stateMutable.value = DependentsState.DependentsLoading(null)
    }
    if (token == "Error") {
      stateMutable.value = DependentsState.DependentsLookupError(null)
    }
    //If ekirjastoToken lookup successful, set the token into the state
    else {
      stateMutable.value = DependentsState.DependentsTokenFound(token)
    }
  }

  /**
   * Post dependent information to the server.
   */
  fun postDependent(dependentName: String, email: String, lang : String) {
      //Get correct dependent
      val correctDependent = dependentList.value?.find { it.firstName == dependentName }

      if (correctDependent != null) {
        //Add email and parent's language to the dependent
        correctDependent.email = email
        correctDependent.locale = lang

        //Post the dependent
        this.viewModelScope.launch(Dispatchers.IO) {
          val dependentPostRequest = createDependentPost(correctDependent)
          dependentPostRequest.execute().use { response ->
            when (val status = response.status) {
              is LSHTTPResponseStatus.Responded.OK -> {
                //Inform user that the post went through correctly
                logger.debug("Server responded OK")
                error.postValue("Success")
                return@launch
              }
              is LSHTTPResponseStatus.Responded.Error -> {
                //Handle error by logging and informing user
                logger.warn("postDependent error: {}", response)
                logger.warn("Error ${response.status}: ${response.properties?.message}")
                error.postValue("Error")
              }
              is LSHTTPResponseStatus.Failed -> {
                //Handle error by logging and informing user
                logger.warn("postDependent failed: {}", response)
                logger.warn("Failed ${response.status}: ${response.properties?.message}")
                error.postValue("Failed")
              }
            }
          }
        }
      }  else {
        logger.debug("No dependent with that name")
      }
    }

  fun lookupDependents() {
    //Get the dependents
    steps.beginNewStep("Start dependents lookup")
//Get account information
    //Get id of default account, that we are always on
    val accountId = getDefaultAccount()!!.id
    //We only have one profile, ekirjasto
    val profile = profiles.profileCurrent()
    //Get the matching account from ekirjasto
    val account = profile.account(accountId)
    //Credentials are of the type Ekirjasto always
    val credentials = account.loginState.credentials as? AccountAuthenticationCredentials.Ekirjasto
    //AuthDescription contains URIs used
    val ekirjastoAuthDescription =
      account.provider.authentication as? AccountProviderAuthenticationDescription.Ekirjasto
    steps.beginNewStep("Get token and patron info from profile")

    val patron = credentials?.patronPermanentID
    val dependentURI = URI(ekirjastoAuthDescription?.relations.toString().replace("patron", patron!!))
    //Create dependents request, using ekirjastotoken as bearer

    steps.beginNewStep("Get Ekirjasto token")
    val future = dependentsHttp.fetchURI(
      dependentURI,
      token!!,
      true
    )
    future.map { dependentsHttpResult ->
      logger.debug("dependentsHttpResult: {}", dependentsHttpResult)

      synchronized(dependentsHttpResult) {
        dependentsHttpResults.onNext(dependentsHttpResult)
      }
    }
  }

  /**
   * Create a post request with the dependent as the request body.
   */
  private fun createDependentPost(dependent: Dependent) : LSHTTPRequestType{
    //Get mapper
    val mapper = ObjectMapper()
    //Read dependent into object node
    val body = mapper.valueToTree<ObjectNode>(dependent)
    //Change the object to string
    val bodyString = mapper.writeValueAsString(body)

    this.logger.debug("Post Dependent Request: {}", bodyString)

    //Put the dependent into the request as the body, use ekirjastoToken as bearer
    // Return the request
    return this.http.newRequest(this.dependentPostURI)
      .setAuthorization(
        LSHTTPAuthorizationBearerToken.ofToken(token!!)
      )
      .setMethod(
        LSHTTPRequestBuilderType.Method.Post(
          bodyString.toByteArray(Charset.forName("UTF-8")),
          MIMEType("application", "json", mapOf())
        )
      )
      .build()
  }
  override fun onCleared() {
    super.onCleared()
    subscriptions.clear()
  }
}
