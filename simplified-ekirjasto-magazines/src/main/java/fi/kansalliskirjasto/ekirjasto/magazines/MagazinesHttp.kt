package fi.kansalliskirjasto.ekirjasto.magazines

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.common.util.concurrent.FluentFuture
import com.google.common.util.concurrent.ListeningExecutorService
import org.librarysimplified.http.api.LSHTTPAuthorizationBearerToken
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URI
import java.util.concurrent.Callable


/**
 * Helper class for performing asynchronous HTTP calls for magazine browsing and reading.
 */
class MagazinesHttp(
  private val http: LSHTTPClientType,
  private val exec: ListeningExecutorService,
) {
  private val logger = LoggerFactory.getLogger(MagazinesHttp::class.java)

  private val objectMapper = jacksonObjectMapper()

  fun fetchURI(
    uri: URI,
    circulationToken: String,
  ): FluentFuture<MagazinesHttpResult> {
    return FluentFuture.from(
      exec.submit(
        Callable {
          fetchSynchronously(
            uri = uri,
            circulationToken = circulationToken
          )
        }
      )
    )
  }

  private fun fetchSynchronously(
    uri: URI,
    circulationToken: String
  ): MagazinesHttpResult {
    try {
      logger.debug("fetchSynchronously()")

      val request = http.newRequest(uri)
        .setAuthorization(LSHTTPAuthorizationBearerToken.ofToken(circulationToken))
        .build()
      val response = request.execute()
      when (val status = response.status) {
        is LSHTTPResponseStatus.Responded.OK -> {
          val body = status.bodyStream?.let { bodyAsJsonNode(it) }
          logger.debug("fetchSynchronously OK: {}", body)
          return MagazinesHttpResult.MagazinesHttpSuccess(body!!)
        }
        is LSHTTPResponseStatus.Responded.Error -> {
          logger.warn("fetchSynchronously error: {}", response)
          return MagazinesHttpResult.MagazinesHttpFailure(
            message = "Error ${response.status}: ${response.properties?.message}"
            //exception = e,
          )
        }
        is LSHTTPResponseStatus.Failed -> {
          logger.warn("fetchSynchronously failed: {}", response)
          return MagazinesHttpResult.MagazinesHttpFailure(
            message = "Failed ${response.status}: ${response.properties?.message}"
            //exception = e,
          )
        }
      }
    } catch (e: Exception) {
      logger.error("fetchSynchronously() exception: ", e)

      return MagazinesHttpResult.MagazinesHttpFailure(
        message = e.localizedMessage ?: ""
        //exception = e,
      )
    }
  }

  private fun bodyAsJsonNode(input: InputStream): JsonNode {
    return objectMapper.readTree(input)
  }
}
