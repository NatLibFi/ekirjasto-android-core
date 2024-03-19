package org.nypl.simplified.ui.accounts.ekirjastopasskey

import android.app.Application
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.exceptions.CreateCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription

class AccountEkirjastoPasskeyViewModel (
  private val application: Application,
  private val account: AccountID,
  private val description: AccountProviderAuthenticationDescription.Ekirjasto,
  private val ekirjastoToken: String?
) : ViewModel() {


  fun createPasskeyAsync(requestJson: String) {
//    val createPublicKeyCredentialRequest = CreatePublicKeyCredentialRequest(
//      requestJson = requestJson
//    )
//
//    try {
//        val result = credentialManager.createCredential(
//        context = requireContext(),
//        request = createPublicKeyCredentialRequest,
//      )
//    } catch (e : CreateCredentialException){
//      handleFailure(e)
//      return@launch
//    }
//
//    // TODO Gather correct json information. Maybe result.data?
//    var result: LSHTTPResponseStatus.Responded.OK? = null
//    try {
//      result = passkeyRequest(
//        parameters.authenticationDescription.passkey_register_finish,
//        "{\"email\": \"email\", \"data\": \"data\"}"
//      )
//    } catch (e : Exception) {
//      handleFailure(e)
//      return@launch
//    }
//
//    finishPasskey(result)

  }

}
