package org.nypl.simplified.accounts.source.ekirjasto

import org.librarysimplified.http.api.LSHTTPProblemReport
import org.nypl.simplified.parser.api.ParseError
import org.nypl.simplified.parser.api.ParseWarning
import java.net.URI

/**
 * An exception raised by the E-kirjasto registry implementation.
 */

sealed class AccountProviderSourceEkirjastoRegistryException(
  message: String,
  cause: Exception? = null
) : Exception(message, cause) {

  /**
   * We failed to connect to the server at all.
   */

  class ServerConnectionFailure(
    val uri: URI,
    cause: Exception
  ) : AccountProviderSourceEkirjastoRegistryException(cause.message ?: "", cause)

  /**
   * The server returned an error instead of a set of account providers.
   */

  class ServerReturnedError(
    val uri: URI,
    val errorCode: Int,
    message: String,
    val problemReport: LSHTTPProblemReport?
  ) : AccountProviderSourceEkirjastoRegistryException(message)

  /**
   * The server returned data that could not be parsed.
   */

  class ServerReturnedUnparseableData(
    val uri: URI,
    val warnings: List<ParseWarning>,
    val errors: List<ParseError>,
    cause: Exception? = null
  ) : AccountProviderSourceEkirjastoRegistryException(cause?.message ?: "", cause)


  class CacheFilesDirNull(
    cause: Exception? = null
  ) : AccountProviderSourceEkirjastoRegistryException(cause?.message ?: "Cahce files dir variable is null.", cause)

  class RemoteDescriptionForDefaultProviderNotFound(
    cause: Exception? = null
  ) : AccountProviderSourceEkirjastoRegistryException(cause?.message ?: "Remote description for the default provider not found.", cause)
}
