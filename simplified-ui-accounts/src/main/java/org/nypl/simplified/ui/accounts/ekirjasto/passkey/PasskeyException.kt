package org.nypl.simplified.ui.accounts.ekirjasto.passkey

import org.librarysimplified.http.api.LSHTTPResponseProperties

sealed class PasskeyException(
  message: String,
  cause: Throwable? = null
) : Exception(message, cause)

class PasskeyFinishException(
  message: String,
  val responseProperties: LSHTTPResponseProperties,
  cause: Throwable? = null
) : PasskeyException(message, cause)
