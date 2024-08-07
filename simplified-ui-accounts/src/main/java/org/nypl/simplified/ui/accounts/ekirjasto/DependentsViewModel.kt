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
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.charset.Charset

class DependentsViewModel(
) : ViewModel() {

  private val services = Services.serviceDirectory()
  private val profiles = services.requireService(ProfilesControllerType::class.java)
  private val http = this.services.requireService(LSHTTPClientType::class.java)
  private val logger = LoggerFactory.getLogger(DependentsViewModel::class.java)
  private val steps: TaskRecorderType = TaskRecorder.create()

  private val dependentList = MutableLiveData<List<Dependent>>()

  val dependentListLive: LiveData<List<Dependent>>
    get() = dependentList

  private lateinit var ekirjastoToken : String


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

  fun lookupDependents() {
    //Hae profiilitiedot
    steps.beginNewStep("Get token and patron info from profile")
    val accountId = getDefaultAccount()!!.id
    val profile = profiles.profileCurrent()
    val account = profile.account(accountId)
    val credentials = account.loginState.credentials as? AccountAuthenticationCredentials.Ekirjasto
    val circulationToken = credentials?.accessToken
    val patron = credentials?.patronInfo
    steps.currentStepSucceeded("Token and patron found in profile")

    logger.debug("Circ token {}", circulationToken)
    logger.debug("Patron {}", patron)
    //TOKEN HAKU
    steps.beginNewStep("Get Ekirjasto token")
    this.viewModelScope.launch(Dispatchers.IO) {
      val tokenRequest = createGetTokenRequest(circulationToken!!)
      tokenRequest.execute().use { response ->
        when (val status = response.status) {
          is LSHTTPResponseStatus.Responded.OK -> {
            //return gives us a token that is to be used in loikka calls
            val mapper = ObjectMapper()
            val jsonNode = mapper.readTree(status.bodyStream)
            //extract the token from answer
            ekirjastoToken = jsonNode.get("token").asText()
            //print the return to see what they look like
            logger.debug(jsonNode.toPrettyString())
            steps.currentStepSucceeded("Got Ekirjasto token")
          }
          is LSHTTPResponseStatus.Failed -> TODO()
          is LSHTTPResponseStatus.Responded.Error -> TODO()
        }
      }
      //HUOLLETTAVAT HAKU
      steps.beginNewStep("Start dependents lookup")
      val dependentsRequest = createDependentsRequest(patron!!, ekirjastoToken)
      dependentsRequest.execute().use { response ->
        when (val status = response.status) {
          is LSHTTPResponseStatus.Responded.OK -> {
            //currently call returns list of items, that are the dependents
            //the list is called items (for some reason)

            //Get a mapper for mapping the dependent values
            val mapper = ObjectMapper()
            //Read into json node the server answer
            val jsonNode = mapper.readTree(status.bodyStream)
            logger.debug(jsonNode.toPrettyString()) //<- prints the return value
            //create an array node from the values listed as items
            val arrayNode = jsonNode["items"] as ArrayNode
            //get an iterator of the elements aka. dependents
            val itr = arrayNode.elements()
            steps.currentStepSucceeded("Dependents looked up and listed")

            //list of dependents that are returned to fragment
            val depends = mutableListOf<Dependent>()
            //iterate through the dependents as long as there are values
            while (itr.hasNext()) {
              logger.debug("Has a child")
              val dep = itr.next()
              //Convert into a dependent
              val user = Dependent(
                firstName = dep["firstName"].asText(),
                lastName = dep["lastName"].asText(),
                govId = dep["govId"].asText()
              )
              logger.debug("This child: {}", user.toString())
              // add user to list
              depends.add(user)
            }
            steps.beginNewStep("Update dependents list started")
            dependentList.postValue(depends)
            steps.currentStepSucceeded("Dependents list updated")
            return@launch
          }

          is LSHTTPResponseStatus.Failed -> TODO()
          is LSHTTPResponseStatus.Responded.Error -> TODO()
        }
      }
    }
  }

  private fun createGetTokenRequest(accessToken: String): LSHTTPRequestType {
    val currentekirjastotokenURI =
      URI("https://lib-dev.e-kirjasto.fi/ekirjasto/ekirjasto_token?provider=E-kirjasto+provider+for+circulation+manager")

    return this.http.newRequest(currentekirjastotokenURI)
      .setAuthorization(
        LSHTTPAuthorizationBearerToken.ofToken(accessToken)
      )
      .build()
  }

  private fun createDependentsRequest(patron: String, token: String) : LSHTTPRequestType{
    val relationsUri = URI("https://e-kirjasto.loikka.dev/v1/identities/${patron}/relations")

    //request from loikka the dependents, set the token we just got as the bearertoken
    return this.http.newRequest(relationsUri)
      .setAuthorization(
        LSHTTPAuthorizationBearerToken.ofToken(token)
      )
      .build()
  }

  private fun handleError() {

  }
  fun postDependent(dependentName: String, email: String, lang : String) {
      //prep correct dependent
      val correctDependent = dependentList.value?.find { it.firstName == dependentName }

      if (correctDependent != null) {
        //add email to info and post
        correctDependent.email = "test@example.com" //email
        correctDependent.locale = lang

        this.viewModelScope.launch(Dispatchers.IO) {
          val dependentPostRequest = createDependentPost(correctDependent)
          dependentPostRequest.execute().use { response ->
            when (val status = response.status) {
              is LSHTTPResponseStatus.Responded.OK -> {
                logger.debug("Server responded OK")
                return@launch
              }

              is LSHTTPResponseStatus.Failed -> TODO()
              is LSHTTPResponseStatus.Responded.Error -> logger.error("threw error")
            }
          }
        }
      }  else {
        logger.debug("No dependent with that name")
      }
    }


  private fun createDependentPost(dependent: Dependent) : LSHTTPRequestType{
    val mapper = ObjectMapper()
    val body = mapper.valueToTree<ObjectNode>(dependent)
    val bodyString = mapper.writeValueAsString(body)

    this.logger.debug("Post Dependent Request: {}", bodyString)

    val dependentsPostURI = URI("https://e-kirjasto.loikka.dev/v1/identities/invite")

    return this.http.newRequest(dependentsPostURI)
      .setAuthorization(
        LSHTTPAuthorizationBearerToken.ofToken(ekirjastoToken)
      )
      .setMethod(
        LSHTTPRequestBuilderType.Method.Post( //Post the dependent's info not implemented
          bodyString.toByteArray(Charset.forName("UTF-8")),
          MIMEType("application", "json", mapOf())
        )
      )
      .build()
  }
}
