package fi.kansalliskirjasto.ekirjasto.magazines

import com.fasterxml.jackson.databind.JsonNode


/**
 * The result of an asynchronous HTTP request related to magazine browsing and reading.
 */
sealed class MagazinesHttpResult {
  /**
   * Successful HTTP result.
   */
  data class MagazinesHttpSuccess(
    val body: JsonNode
  ) : MagazinesHttpResult()

  /**
   * Failed HTTP result
   */
  data class MagazinesHttpFailure(
    val message: String?
  ) : MagazinesHttpResult()
}
