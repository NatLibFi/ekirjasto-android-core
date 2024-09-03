package org.nypl.simplified.ui.accounts.ekirjasto

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
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
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.charset.Charset

class DependentsViewModel(
) : ViewModel() {

  //Get the services needed for functionalities
  private val services = Services.serviceDirectory()
  private val profiles = services.requireService(ProfilesControllerType::class.java)
  private val http = services.requireService(LSHTTPClientType::class.java)
  private val logger = LoggerFactory.getLogger(DependentsViewModel::class.java)
  private val steps: TaskRecorderType = TaskRecorder.create()

  //List of the dependents returned by the server
  private val dependentList = MutableLiveData<List<Dependent>>()

  //Observable dependent list
  val dependentListLive: LiveData<List<Dependent>>
    get() = dependentList

  //Ekirjasto token returned by circulation
  private lateinit var ekirjastoToken : String
  //Dependent post URI
  private lateinit var dependentPostURI: URI

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
   * Handle the dependents lookup. Function makes a call to circulation to get ekirjasto token,
   * and uses it to get dependents from API. Updates the found dependents to the observable
   * dependentList.
   */
  fun lookupDependents() {
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
    val patron = credentials?.patronPermanentID
    steps.currentStepSucceeded("Token and patron found in profile")
    steps.beginNewStep("Get the dependent and token URIs from description")
    val ekirjastoTokenUrl = ekirjastoAuthDescription?.ekirjasto_token
    val dependentURI = URI(ekirjastoAuthDescription?.relations.toString().replace("patron", patron!!))
    this.dependentPostURI = ekirjastoAuthDescription?.invite!!
    steps.currentStepSucceeded("URIs found in description")

    //Get the ekirjastoToken from circulation
    steps.beginNewStep("Get Ekirjasto token")
    this.viewModelScope.launch(Dispatchers.IO) {
      // Create the request for ekirjasto token, using circulation token as bearer
      val tokenRequest = createGetRequest(ekirjastoTokenUrl!!, circulationToken!!)
      tokenRequest.execute().use { response ->
        when (val status = response.status) {
          is LSHTTPResponseStatus.Responded.OK -> {
            //return gives us a token that is to be used in loikka calls
            val mapper = ObjectMapper()
            val jsonNode = mapper.readTree(status.bodyStream)
            //extract the token from answer
            ekirjastoToken = jsonNode.get("token").asText()
            steps.currentStepSucceeded("Got Ekirjasto token")
          }
          is LSHTTPResponseStatus.Responded.Error -> {
            //Handle error by logging and informing user
            logger.warn("fetchEkirjastoToken error: {}", response)
            logger.warn("Error ${response.status}: ${response.properties?.message}")
            steps.currentStepFailed("Error getting ekirjastoToken", response.properties!!.message)
            error.postValue("Error")
          }
          is LSHTTPResponseStatus.Failed -> {
            //Handle error by logging and informing user
            logger.warn("fetchEkirjastoToken failed: {}", response)
            logger.warn("Failed ${response.status}: ${response.properties?.message}")
            steps.currentStepFailed("Failed to get ekirjastoToken", response.properties!!.message)
            error.postValue("Failed")
          }
        }
      }
      //Get the dependents
      steps.beginNewStep("Start dependents lookup")

      //Create dependents request, using ekirjastotoken as bearer
      val dependentsRequest = createGetRequest(dependentURI, ekirjastoToken)
      dependentsRequest.execute().use { response ->
        when (val status = response.status) {
          is LSHTTPResponseStatus.Responded.OK -> {
            //currently call returns list of items, that are the dependents
            //the list is called items (for some reason)

            //Get a mapper for mapping the dependent values
            val mapper = ObjectMapper()
            //Read into json node the server answer
            val jsonNode = mapper.readTree(status.bodyStream)
            //create an array node from the values listed as items
            val arrayNode = jsonNode["items"] as ArrayNode
            //get an iterator of the elements aka. dependents
            val itr = arrayNode.elements()
            steps.currentStepSucceeded("Dependents looked up and listed")

            //list of dependents that are returned to fragment
            val dependents = mutableListOf<Dependent>()
            //iterate through the dependents as long as there are values
            while (itr.hasNext()) {
              //Next dependent from the list
              val dep = itr.next()
              //Convert into a dependent
              val user = Dependent(
                firstName = dep["firstName"].asText(),
                lastName = dep["lastName"].asText(),
                govId = dep["govId"].asText()
              )
              // add user to list
              dependents.add(user)
            }
            steps.beginNewStep("Update dependents list started")
            //Update the observable dependents list
            dependentList.postValue(dependents)
            steps.currentStepSucceeded("Dependents list updated")
            return@launch
          }

          is LSHTTPResponseStatus.Responded.Error -> {
            //Handle error by logging and informing user
            logger.warn("fetchDependents error: {}", response)
            logger.warn("Error ${response.status}: ${response.properties?.message}")
            error.postValue("Error")

          }
          is LSHTTPResponseStatus.Failed -> {
            //Handle error by logging and informing user
            logger.warn("fetchDependents failed: {}", response)
            logger.warn("Failed ${response.status}: ${response.properties?.message}")
            error.postValue("Failed")
          }
        }
      }
    }
  }

  /**
   * Create a get request to provided uri with provided token.
   */
  private fun createGetRequest(targetURI: URI, bearerToken: String): LSHTTPRequestType {
    //Return the request
    return this.http.newRequest(targetURI)
      .setAuthorization(
        LSHTTPAuthorizationBearerToken.ofToken(bearerToken)
      )
      .build()
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
        LSHTTPAuthorizationBearerToken.ofToken(ekirjastoToken)
      )
      .setMethod(
        LSHTTPRequestBuilderType.Method.Post(
          bodyString.toByteArray(Charset.forName("UTF-8")),
          MIMEType("application", "json", mapOf())
        )
      )
      .build()
  }
}
