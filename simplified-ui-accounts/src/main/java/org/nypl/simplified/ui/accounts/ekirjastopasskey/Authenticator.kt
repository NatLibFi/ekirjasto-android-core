package org.nypl.simplified.ui.accounts.ekirjastopasskey

import android.app.Application
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels.authenticate.AuthenticateParameters
import org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels.authenticate.AuthenticateResult
import org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels.authenticate.PublicKeyCredentialRequestOptions
import org.slf4j.LoggerFactory

/**
 * Wrapper for android credential manager
 */
class Authenticator (
  val application: Application,
  val credentialManager: CredentialManager )
{
  val objectMapper = jacksonObjectMapper()
  val logger = LoggerFactory.getLogger(Authenticator::class.java)

  suspend fun authenticate(parameters: AuthenticateParameters): AuthenticateResult {
    val options = PublicKeyCredentialRequestOptions.from(parameters)
    val credOption = GetPublicKeyCredentialOption(objectMapper.writeValueAsString(options))
    val request = GetCredentialRequest.Builder()
      .addCredentialOption(credOption)
      .build()
    var result: GetCredentialResponse? = null
    try {
      result = credentialManager.getCredential(application, request)
    } catch (e: Exception){
      logger.error("Authenticate Error",e)
    }
    result?.let {
      try{
        when (val cred = it.credential){
          is PublicKeyCredential -> {
            return AuthenticateResult.parseJson(cred)
          }
          else -> throw Exception("Invalid credential type: ${cred.javaClass.name}")
        }
      } catch(e: JsonParseException){
        logger.error("Error parsing response", e)
      }
    }

    return AuthenticateResult.Failure()

  }

}
