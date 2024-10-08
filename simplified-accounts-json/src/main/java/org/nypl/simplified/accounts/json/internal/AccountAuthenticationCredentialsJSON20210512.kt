package org.nypl.simplified.accounts.json.internal

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountAuthenticationTokenInfo
import org.nypl.simplified.accounts.api.AccountCookie
import org.nypl.simplified.accounts.api.AccountPassword
import org.nypl.simplified.accounts.api.AccountUsername
import org.nypl.simplified.json.core.JSONParseException
import org.nypl.simplified.json.core.JSONParserUtilities
import org.slf4j.LoggerFactory
import java.net.URI

/**
 * JSON format version 20210512.
 */

object AccountAuthenticationCredentialsJSON20210512 : AccountAuthenticationCredentialsJSONVersionedType {

  private val logger =
    LoggerFactory.getLogger(AccountAuthenticationCredentialsJSON20210512::class.java)

  override val supportedVersion: Int =
    20210512

  override fun deserializeFromJSON(
    node: JsonNode
  ): AccountAuthenticationCredentials {
    logger.debug("deserializing version 20210512")

    val obj =
      JSONParserUtilities.checkObject(null, node)

    return when (val type = JSONParserUtilities.getString(obj, "@type")) {
      "basic" -> {
        deserializeBasic(obj)
      }
      "basicToken" -> {
        deserializeBasicToken(obj)
      }
      "oauthWithIntermediary" -> {
        deserializeOAuthWithIntermediary(obj)
      }
      "saml2_0" -> {
        deserializeSAML2_0(obj)
      }
      "ekirjasto" -> {
        deserializeEkirjasto(obj)
      }
      else -> {
        throw JSONParseException("Unrecognized type: $type")
      }
    }
  }

  private fun deserializeOAuthWithIntermediary(
    obj: ObjectNode
  ): AccountAuthenticationCredentials.OAuthWithIntermediary {
    val adobeCredentials =
      JSONParserUtilities.getObjectOrNull(obj, "adobe_credentials")
        ?.let(AccountAuthenticationCredentialsAdobeJSON::deserializeAdobeCredentials)

    return AccountAuthenticationCredentials.OAuthWithIntermediary(
      accessToken = JSONParserUtilities.getString(obj, "accessToken"),
      adobeCredentials = adobeCredentials,
      authenticationDescription = JSONParserUtilities.getStringOrNull(obj, "authenticationDescription"),
      annotationsURI = JSONParserUtilities.getURIOrNull(obj, "annotationsURI"),
      deviceRegistrationURI = JSONParserUtilities.getURIOrNull(obj, "deviceRegistrationURI")
    )
  }

  private fun deserializeBasic(
    obj: ObjectNode
  ): AccountAuthenticationCredentials.Basic {
    val adobeCredentials =
      JSONParserUtilities.getObjectOrNull(obj, "adobe_credentials")
        ?.let(AccountAuthenticationCredentialsAdobeJSON::deserializeAdobeCredentials)

    return AccountAuthenticationCredentials.Basic(
      userName = AccountUsername(JSONParserUtilities.getString(obj, "username")),
      password = AccountPassword(JSONParserUtilities.getString(obj, "password")),
      adobeCredentials = adobeCredentials,
      authenticationDescription = JSONParserUtilities.getStringOrNull(obj, "authenticationDescription"),
      annotationsURI = JSONParserUtilities.getURIOrNull(obj, "annotationsURI"),
      deviceRegistrationURI = JSONParserUtilities.getURIOrNull(obj, "deviceRegistrationURI")
    )
  }

  private fun deserializeBasicToken(
    obj: ObjectNode
  ): AccountAuthenticationCredentials.BasicToken {
    val adobeCredentials =
      JSONParserUtilities.getObjectOrNull(obj, "adobe_credentials")
        ?.let(AccountAuthenticationCredentialsAdobeJSON::deserializeAdobeCredentials)

    val authenticationTokenInfo =
      JSONParserUtilities.getObjectOrNull(obj, "authenticationTokenInfo")
        ?: throw Exception("No authentication token info")

    return AccountAuthenticationCredentials.BasicToken(
      userName = AccountUsername(JSONParserUtilities.getString(obj, "username")),
      password = AccountPassword(JSONParserUtilities.getString(obj, "password")),
      authenticationTokenInfo = AccountAuthenticationTokenInfo(
        accessToken = authenticationTokenInfo.get("accessToken").asText(),
        authURI = URI.create(authenticationTokenInfo.get("authURI").asText()),
      ),
      adobeCredentials = adobeCredentials,
      authenticationDescription = JSONParserUtilities.getStringOrNull(obj, "authenticationDescription"),
      annotationsURI = JSONParserUtilities.getURIOrNull(obj, "annotationsURI"),
      deviceRegistrationURI = JSONParserUtilities.getURIOrNull(obj, "deviceRegistrationURI")
    )
  }

  private fun deserializeSAML2_0(obj: ObjectNode): AccountAuthenticationCredentials {
    val adobeCredentials =
      JSONParserUtilities.getObjectOrNull(obj, "adobe_credentials")
        ?.let(AccountAuthenticationCredentialsAdobeJSON::deserializeAdobeCredentials)

    return AccountAuthenticationCredentials.SAML2_0(
      accessToken = JSONParserUtilities.getString(obj, "accessToken"),
      adobeCredentials = adobeCredentials,
      authenticationDescription = JSONParserUtilities.getStringOrNull(obj, "authenticationDescription"),
      patronInfo = JSONParserUtilities.getString(obj, "patronInfo"),
      cookies = deserializeCookies(JSONParserUtilities.getArray(obj, "cookies")),
      annotationsURI = JSONParserUtilities.getURIOrNull(obj, "annotationsURI"),
      deviceRegistrationURI = JSONParserUtilities.getURIOrNull(obj, "deviceRegistrationURI")
    )
  }

  private fun deserializeEkirjasto(obj: ObjectNode): AccountAuthenticationCredentials {
    val adobeCredentials =
      JSONParserUtilities.getObjectOrNull(obj, "adobe_credentials")
        ?.let(AccountAuthenticationCredentialsAdobeJSON::deserializeAdobeCredentials)

    return AccountAuthenticationCredentials.Ekirjasto(
      accessToken = JSONParserUtilities.getString(obj, "accessToken"),
      ekirjastoToken = JSONParserUtilities.getStringOrNull(obj, "ekirjastoToken")?:"",
      patronPermanentID = JSONParserUtilities.getString(obj, "patronPermanentID")?:"",
      adobeCredentials = adobeCredentials,
      authenticationDescription = JSONParserUtilities.getStringOrNull(obj, "authenticationDescription"),
      annotationsURI = JSONParserUtilities.getURIOrNull(obj, "annotationsURI"),
      deviceRegistrationURI = null
    )
  }

  private fun deserializeCookies(
    array: ArrayNode
  ): List<AccountCookie> {
    val results = mutableListOf<AccountCookie>()

    for (index in 0 until array.size()) {
      val obj = JSONParserUtilities.checkObject(index.toString(), array[index])

      results.add(
        AccountCookie(
          JSONParserUtilities.getString(obj, "url"),
          JSONParserUtilities.getString(obj, "value")
        )
      )
    }

    return results
  }
}
