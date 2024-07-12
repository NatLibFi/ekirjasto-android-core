package org.nypl.simplified.books.controller

import one.irradia.mime.api.MIMECompatibility
import org.librarysimplified.http.api.LSHTTPAuthorizationBearerToken
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPRequestBuilderType
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.nypl.simplified.accounts.api.AccountLoginStringResourcesType
import org.nypl.simplified.profiles.controller.api.ProfileDependentsPostRequest
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.Callable

class ProfileDependentsPostTask (
  private val http: LSHTTPClientType,
  private val loginStrings: AccountLoginStringResourcesType,
  private val request: ProfileDependentsPostRequest
  ) : Callable<TaskResult<Unit>> {

    private val steps: TaskRecorderType =
      TaskRecorder.create()

    private val logger =
      LoggerFactory.getLogger(ProfileDependentsPostTask::class.java)

    override fun call() =
      this.run()

    private fun run(): TaskResult<Unit> {
      return try {
        when (this.request) {
          is ProfileDependentsPostRequest.Ekirjasto -> {
            logger.debug("Dependents post request started")
            this.runPostRequest(this.request)
          }
        }
      } catch (e: Throwable) {
        this.logger.error("error during dependent post process: ", e)
        this.steps.currentStepFailedAppending(
          this.loginStrings.loginUnexpectedException, "unexpectedException", e
        )
        val failure = this.steps.finishFailure<Unit>()
        failure
      }
    }

    private fun handleProfileDependentPostError(
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

    private fun runPostRequest(
      request: ProfileDependentsPostRequest.Ekirjasto
    ): TaskResult<Unit> {
      this.logger.warn("ProfileDependentsPostRequest Executing")
      //this should be the correct URI, but has not been tested yet
      val dependentsPostURI = URI("https://e-kirjasto.loikka.dev/v1/identities/invite")

      val httpRequest = this.http.newRequest(dependentsPostURI)
        .setAuthorization(
          LSHTTPAuthorizationBearerToken.ofToken(request.ekirjastoToken)
        )
        .setMethod(
          LSHTTPRequestBuilderType.Method.Post( //Post the dependent's info not implemented
            ByteArray(0),
            MIMECompatibility.applicationOctetStream
          )
        )
        .build()
      this.steps.beginNewStep("Post Dependent")
      httpRequest.execute().use { response ->
        when (val status = response.status) {
          is LSHTTPResponseStatus.Responded.OK -> {
            //TODO do something with server response if needed
            this.steps.currentStepSucceeded("Dependent post successful")

            return this.steps.finishSuccess(Unit)
          }

          is LSHTTPResponseStatus.Responded.Error -> {
            handleProfileDependentPostError(dependentsPostURI, status)
            return this.steps.finishFailure()
          }

          is LSHTTPResponseStatus.Failed -> {
            this.steps.currentStepFailed(
              "Connection failed when posting.",
              "connectionFailed",
              status.exception
            )
            throw status.exception
          }
        }
      }
    }
  }

