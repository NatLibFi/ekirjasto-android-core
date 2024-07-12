package org.nypl.simplified.books.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.librarysimplified.http.api.LSHTTPAuthorizationBearerToken
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.nypl.simplified.accounts.api.AccountLoginStringResourcesType
import org.nypl.simplified.profiles.controller.api.ProfileDependentsLookupRequest
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.Callable

/**
 * Task for looking up the dependents of a user.
 * This handles the http call but does not do
 * anything with the information yet
 */
class ProfileDependentsLookupTask (
  private val http: LSHTTPClientType,
  private val loginStrings: AccountLoginStringResourcesType,
  private val request: ProfileDependentsLookupRequest
  ) : Callable<TaskResult<Unit>> {

    private val steps: TaskRecorderType =
      TaskRecorder.create()

    private val logger =
      LoggerFactory.getLogger(ProfileDependentsLookupTask::class.java)

    override fun call() =
      this.run()

    private fun run(): TaskResult<Unit> {
      return try {
        when (this.request) {
          is ProfileDependentsLookupRequest.Ekirjasto -> {
            //Currently there are no other types, and I just named it to ekirjasto, might not need one
            logger.debug("Starting lookup request fulfil")
            this.runLookupRequest(this.request)
          }
        }
      } catch (e: Throwable) {
        this.logger.error("error during lookup process: ", e)
        this.steps.currentStepFailedAppending(
          this.loginStrings.loginUnexpectedException, "unexpectedException", e
        )
        val failure = this.steps.finishFailure<Unit>()
        failure
      }
    }

    private fun handleDependentsLookupError(
      uri: URI,
      result: LSHTTPResponseStatus.Responded.Error
    ) {
      this.logger.error(
        "received http error: {}: {}: {}",
        uri,
        result.properties.message,
        result.properties.status
      )

      val exception = Exception()
      when (result.properties.status) {
        HttpURLConnection.HTTP_UNAUTHORIZED -> {
          this.steps.currentStepFailed("Invalid credentials!", "invalidCredentials", exception)
          throw exception
        }

        else -> {
          this.steps.addAttributesIfPresent(result.properties.problemReport?.toMap())
          this.steps.currentStepFailed(
            "Server error: ${result.properties.status} ${result.properties.message}",
            "httpError ${result.properties.status} $uri",
            exception
          )
          throw exception
        }
      }
    }

    private fun runLookupRequest(
      request: ProfileDependentsLookupRequest.Ekirjasto
    ): TaskResult<Unit> {
      this.logger.warn("ProfileDependentsLookup")
      /*Currently testable URIs. Aparently only the testuser with child can be called correctly
        with the relations URI, but the userinfo should work for all users, if token is correct
        val relationsUri = URI("https://e-kirjasto.loikka.dev/v1/identities/${patron}/relations")
        val userinfoUri = URI("https://e-kirjasto.loikka.dev/v1/auth/userinfo")
        val testuserwithchildUri = URI("https://e-kirjasto.loikka.dev/v1/identities/44da420c-4fbf-4ed2-9733-61e0476121a0/relations")
        */
      val dependentsURI = URI("https://e-kirjasto.loikka.dev/v1/auth/userinfo")

      val httpRequest = this.http.newRequest(dependentsURI)
        .setAuthorization(
          LSHTTPAuthorizationBearerToken.ofToken(request.ekirjastoToken)
        )
        .build()
      this.steps.beginNewStep("Lookup patron's dependents")
      httpRequest.execute().use { response ->
        when (val status = response.status) {
          is LSHTTPResponseStatus.Responded.OK -> {
            //currently call returns list of items, that are the dependents
            //the list is called items (for some reason)
            //here they are only printed
            val mapper = ObjectMapper()
            val jsonNode = mapper.readTree(status.bodyStream)
            logger.debug(jsonNode.toPrettyString()) //print the return to see what they look like
            this.steps.currentStepSucceeded("Dependent lookup successful")

            return this.steps.finishSuccess(Unit)
          }

          is LSHTTPResponseStatus.Responded.Error -> {
            handleDependentsLookupError(dependentsURI, status)
            return this.steps.finishFailure()
          }

          is LSHTTPResponseStatus.Failed -> {
            this.steps.currentStepFailed(
              "Connection failed when fetching authentication token.",
              "connectionFailed",
              status.exception
            )
            throw status.exception
          }
        }
      }
    }
  }

