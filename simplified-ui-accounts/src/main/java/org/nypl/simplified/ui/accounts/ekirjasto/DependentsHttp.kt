package org.nypl.simplified.ui.accounts.ekirjasto

import androidx.lifecycle.viewModelScope
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.common.util.concurrent.FluentFuture
import com.google.common.util.concurrent.ListeningExecutorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.librarysimplified.http.api.LSHTTPAuthorizationBearerToken
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URI
import java.util.concurrent.Callable


/**
 * Helper class for performing asynchronous HTTP calls for dependents lookup.
 */
class DependentsHttp(
  private val http: LSHTTPClientType,
  private val exec: ListeningExecutorService,
) {
  private val logger = LoggerFactory.getLogger(DependentsHttp::class.java)

  /**
   * The main function called to make HTTP requests. Triggers different functions depending on
   * if it's a dependent or token lookup.
   *
   * @return FluentFuture of the result of the HTTP request
   */

  fun fetchURI(
    uri: URI,
    token: String,
    dependentLookup: Boolean
  ): FluentFuture<DependentsHttpResult> {
    //If the lookup is for fependents, execute dependents lookup
    if (dependentLookup) {
      return FluentFuture.from(
        exec.submit(
          Callable {
            fetchDependentsSynchronously(
              uri = uri,
              ekirjastoToken = token
            )
          }
        )
      )
    } else {
      //If not dependents lookup, it's a token lookup so trigger ekirjastoToken lookup
      return FluentFuture.from(
        exec.submit(
          Callable {
            fetchTokenSynchronously(
              uri = uri,
              circulationToken = token
            )
          }
        )
      )
    }
  }

  /**
   * Function to handle the lookup of dependents. The ekirjastoToken lookup needs to have happened
   * before calling this function.
   *
   * @return DependentsHttpResult - if successful the dependents are listed in the result in a mutable list
   */

  private fun fetchDependentsSynchronously(
    uri: URI,
    ekirjastoToken: String
  ): DependentsHttpResult {
    logger.debug("fetchDependentsSynchronously()")
    //Use the ekirjastoToken as the bearer for the request
    val request = http.newRequest(uri)
      .setAuthorization(LSHTTPAuthorizationBearerToken.ofToken(ekirjastoToken))
      .build()

    val response = request.execute()

    when (val status = response.status) {
      is LSHTTPResponseStatus.Responded.OK -> {
        //If response ok, collect the dependent information from the response

        //Get a mapper for mapping the dependent values
        val mapper = ObjectMapper()
        //Read into json node the server answer
        val jsonNode = mapper.readTree(status.bodyStream)
        //create an array node from the values listed as items
        val arrayNode = jsonNode["items"] as ArrayNode
        //get an iterator of the elements aka. dependents
        val itr = arrayNode.elements()

        //list of dependents that are returned
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
        logger.debug("List of dependents collected")

        //Return a successful result with the dependents
        return DependentsHttpResult.DependentsHttpDependentLookupSuccess(
          dependents
        )
      }

      is LSHTTPResponseStatus.Responded.Error -> {
        //Handle error by logging and informing user
        return DependentsHttpResult.DependentsHttpDependentLookupError(
          message = "Error ${response.status}: ${response.properties?.message}"
        )

      }
      is LSHTTPResponseStatus.Failed -> {
        //Handle failure by logging and informing user
        return DependentsHttpResult.DependentsHttpDependentLookupFailure(
          message = "Error ${response.status}: ${response.properties?.message}"
        )
      }
    }
  }

  /**
   * Function to handle the lookup of the ekirjastoToken.
   * @return DependentsHttpResult - if successful returns the ekirjastoToken
   */

  private fun fetchTokenSynchronously(
    uri: URI,
    circulationToken: String
  ): DependentsHttpResult {
    try {
      logger.debug("fetchTokenSynchronously()")

      //Use the circulation token as bearer
      val request = http.newRequest(uri)
        .setAuthorization(LSHTTPAuthorizationBearerToken.ofToken(circulationToken))
        .build()

      val response = request.execute()

      when (val status = response.status) {
            is LSHTTPResponseStatus.Responded.OK -> {
              //return gives us a token that is to be used in loikka calls
              val mapper = ObjectMapper()
              val jsonNode = mapper.readTree(status.bodyStream)
              //extract the token from answer
              val ekirjastoToken = jsonNode.get("token").asText()
              logger.debug("Received EkirjastoToken")
              //Return result success with the token
              return DependentsHttpResult.DependentsHttpTokenSuccess(ekirjastoToken!!)
            }
            is LSHTTPResponseStatus.Responded.Error -> {
              if (status.properties.originalStatus == 401) {
                //If error is 401, accessToken is expired and should be refreshed
                //Return error result but inform about refresh need
                return DependentsHttpResult.DependentsHttpTokenError(
                  message = "accessToken refresh needed"
                )
              }else {
                //Handle other errors by logging and informing user
                return DependentsHttpResult.DependentsHttpTokenError(
                  message = "Error ${response.status}: ${response.properties?.message}"
                )
              }
            }
            is LSHTTPResponseStatus.Failed -> {
              //Handle error by logging and informing user
              logger.warn("fetchSynchronously failed: {}", response)
              return DependentsHttpResult.DependentsHttpTokenFailure(
                message = "Failed ${response.status}: ${response.properties?.message}"
              )
            }
        }
    } catch (e: Exception) {
      logger.error("fetchTokenSynchronously() exception: ", e)

      return DependentsHttpResult.DependentsHttpTokenFailure(
        message = e.localizedMessage ?: ""
      )
    }
  }
}
