package org.nypl.simplified.accounts.source.nyplregistry

import one.irradia.mime.api.MIMECompatibility
import one.irradia.mime.api.MIMEType
import org.joda.time.DateTime
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.nypl.simplified.accounts.api.AccountProvider
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription.Companion.ANONYMOUS_TYPE
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription.Companion.BASIC_TOKEN_TYPE
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription.Companion.BASIC_TYPE
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription.Companion.COPPA_TYPE
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription.Companion.OAUTH_INTERMEDIARY_TYPE
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription.Companion.SAML_2_0_TYPE
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription.Companion.EKIRJASTO_TYPE
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription.KeyboardInput
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.accounts.api.AccountProviderResolutionListenerType
import org.nypl.simplified.accounts.api.AccountProviderResolutionStringsType
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.source.nyplregistry.AccountProviderResolutionErrorCodes.authDocumentParseFailed
import org.nypl.simplified.accounts.source.nyplregistry.AccountProviderResolutionErrorCodes.authDocumentUnusable
import org.nypl.simplified.accounts.source.nyplregistry.AccountProviderResolutionErrorCodes.authDocumentUnusableLink
import org.nypl.simplified.accounts.source.nyplregistry.AccountProviderResolutionErrorCodes.httpRequestFailed
import org.nypl.simplified.accounts.source.nyplregistry.AccountProviderResolutionErrorCodes.unexpectedException
import org.nypl.simplified.links.Link
import org.nypl.simplified.opds.auth_document.api.AuthenticationDocument
import org.nypl.simplified.opds.auth_document.api.AuthenticationDocumentParsersType
import org.nypl.simplified.opds.auth_document.api.AuthenticationObject
import org.nypl.simplified.opds.auth_document.api.AuthenticationObject.Companion.LABEL_LOGIN
import org.nypl.simplified.opds.auth_document.api.AuthenticationObject.Companion.LABEL_PASSWORD
import org.nypl.simplified.parser.api.ParseResult
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.Locale

/**
 * The logic needed to resolve a description into a full provider using standard NYPL logic.
 */

class AccountProviderResolution(
  private val stringResources: AccountProviderResolutionStringsType,
  private val authDocumentParsers: AuthenticationDocumentParsersType,
  private val http: LSHTTPClientType,
  private val description: AccountProviderDescription
) {

  private val authDocumentType =
    MIMEType("application", "vnd.opds.authentication.v1.0+json", mapOf())

  private val logger =
    LoggerFactory.getLogger(AccountProviderResolution::class.java)

  fun resolve(onProgress: AccountProviderResolutionListenerType): TaskResult<AccountProviderType> {
    val taskRecorder = TaskRecorder.create()
    taskRecorder.addAttribute("Account ID", this.description.id.toString())
    taskRecorder.addAttribute("Account", this.description.title)

    return try {
      this.logger.debug("starting resolution")
      taskRecorder.beginNewStep(this.stringResources.resolving)
      onProgress.invoke(this.description.id, this.stringResources.resolving)

      val authDocument =
        this.fetchAuthDocument(taskRecorder, onProgress)

      val supportsReservations =
        if (authDocument != null) {
          this.supportsReservations(authDocument)
        } else {
          false
        }

      val authentications =
        if (authDocument != null) {
          this.extractAuthenticationDescription(taskRecorder, authDocument)
        } else {
          Pair(AccountProviderAuthenticationDescription.Anonymous, listOf())
        }

      val announcements =
        authDocument?.announcements ?: emptyList()

      val updated =
        DateTime.now()

      /*
       * The catalog URI is required to be present, but we obviously have no guarantee that it
       * actually will be.
       */

      val catalogURI =
        this.findCatalogURI(authDocument, taskRecorder, onProgress)

      val title =
        this.findTitle(authDocument)

      val description =
        this.findDescription(authDocument)

      val alternateURI =
        this.findAlternateLink()

      val accountProvider =
        AccountProvider(
          addAutomatically = this.description.isAutomatic,
          announcements = announcements,
          authentication = authentications.first,
          authenticationAlternatives = authentications.second,
          authenticationDocumentURI = this.description.authenticationDocumentURI?.hrefURI,
          cardCreatorURI = authDocument?.cardCreatorURI,
          catalogURI = catalogURI,
          description = description,
          displayName = title,
          eula = authDocument?.eulaURI,
          id = this.description.id,
          idNumeric = -1,
          isProduction = this.description.isProduction,
          license = authDocument?.licenseURI,
          loansURI = authDocument?.loansURI,
          logo = authDocument?.logoURI ?: this.description.logoURI?.hrefURI,
          mainColor = authDocument?.mainColor ?: "red",
          patronSettingsURI = authDocument?.patronSettingsURI,
          privacyPolicy = authDocument?.privacyPolicyURI,
          resetPasswordURI = authDocument?.resetPasswordURI,
          subtitle = authDocument?.description,
          supportEmail = authDocument?.supportURI?.toString(),
          supportsReservations = supportsReservations,
          updated = updated,
          location = this.description.location,
          alternateURI = alternateURI,
          selectedURI = authDocument?.selectedURI
        )

      taskRecorder.finishSuccess(accountProvider)
    } catch (e: Exception) {
      this.logger.error("failed to resolve account provider: ", e)
      taskRecorder.currentStepFailedAppending(
        message = this.stringResources.resolvingUnexpectedException,
        errorCode = unexpectedException(this.description),
        exception = e
      )
      taskRecorder.finishFailure()
    }
  }

  private fun findAlternateLink(): URI? {
    return this.description.links.firstOrNull { link ->
      link.relation == "alternate"
    }?.hrefURI
  }

  private fun findTitle(
    authDocument: AuthenticationDocument?
  ): String {
    val authTitle = authDocument?.title
    if (authTitle != null) {
      this.logger.debug("took title from authentication document")
      return authTitle
    }

    this.logger.debug("took title from metadata")
    return this.description.title
  }

  private fun findDescription(
    authDocument: AuthenticationDocument?
  ): String {
    val authDescription = authDocument?.description
    if (authDescription != null) {
      this.logger.debug("took description from authentication document")
      return authDescription
    }

    this.logger.debug("took description from metadata")
    return this.description.description.orEmpty()
  }

  private fun findCatalogURI(
    authDocument: AuthenticationDocument?,
    taskRecorder: TaskRecorderType,
    onProgress: AccountProviderResolutionListenerType
  ): URI {
    this.logger.debug("finding catalog URI")

    val authDocumentStartURI = authDocument?.startURI
    if (authDocumentStartURI != null) {
      this.logger.debug("took catalog URI from authentication document")
      return authDocumentStartURI
    }

    val metadataCatalogURI = this.description.catalogURI?.hrefURI
    if (metadataCatalogURI != null) {
      this.logger.debug("took catalog URI from metadata")
      return metadataCatalogURI
    }

    val message = this.stringResources.resolvingAuthDocumentNoStartURI
    taskRecorder.currentStepFailed(message, authDocumentUnusable(this.description))
    onProgress.invoke(this.description.id, message)
    throw IOException()
  }

  private fun supportsReservations(authDocument: AuthenticationDocument): Boolean {
    return authDocument.features.enabled.contains("https://librarysimplified.org/rel/policy/reservations")
  }

  private fun extractAuthenticationDescription(
    taskRecorder: TaskRecorderType,
    authDocument: AuthenticationDocument
  ): Pair<AccountProviderAuthenticationDescription, List<AccountProviderAuthenticationDescription>> {
    if (authDocument.authentication.isEmpty()) {
      return Pair(AccountProviderAuthenticationDescription.Anonymous, listOf())
    }

    val authObjects =
      mutableListOf<AccountProviderAuthenticationDescription>()

    accumulateAuthentications@ for (authObject in authDocument.authentication) {
      when (val authType = authObject.type.toASCIIString()) {
        OAUTH_INTERMEDIARY_TYPE -> {
          authObjects.add(
            this.extractAuthenticationDescriptionOAuthIntermediary(taskRecorder, authObject)
          )
        }

        BASIC_TYPE -> {
          authObjects.add(
            this.extractAuthenticationDescriptionBasic(authObject)
          )
        }

        COPPA_TYPE -> {
          authObjects.add(
            this.extractAuthenticationDescriptionCOPPA(taskRecorder, authObject)
          )
        }

        ANONYMOUS_TYPE -> {
          authObjects.clear()
          authObjects.add(AccountProviderAuthenticationDescription.Anonymous)
          break@accumulateAuthentications
        }

        SAML_2_0_TYPE -> {
          authObjects.add(
            this.extractAuthenticationDescriptionSAML20(taskRecorder, authObject)
          )
        }
        BASIC_TOKEN_TYPE -> {
          authObjects.add(
            extractAuthenticationDescriptionBasicToken(taskRecorder, authObject)
          )
        }

        EKIRJASTO_TYPE -> {
          authObjects.add(
            extractAuthenticationDescriptionEkirjasto(taskRecorder, authObject)
          )
        }
      }
    }

    if (authObjects.isNotEmpty()) {
      val basicTokenAuthObject = authObjects.firstOrNull { authObject ->
        authObject is AccountProviderAuthenticationDescription.BasicToken
      }

      // basic token has the highest priority amongst the auth methods
      return if (basicTokenAuthObject != null) {
        Pair(
          basicTokenAuthObject,
          authObjects.minus(basicTokenAuthObject)
            .filter { it.canBeAlternativeLoginMethod }
        )
      } else {
        val firstAuth = authObjects.first()
        Pair(firstAuth, authObjects.minus(firstAuth).filter { it.canBeAlternativeLoginMethod })
      }
    }

    val message = this.stringResources.resolvingAuthDocumentNoUsableAuthenticationTypes
    taskRecorder.currentStepFailed(message, authDocumentUnusable(this.description))
    throw IOException(message)
  }

  // Finland
  private fun extractAuthenticationDescriptionEkirjasto(
    taskRecorder: TaskRecorderType,
    authObject: AuthenticationObject
  ): AccountProviderAuthenticationDescription {
    val links = mutableMapOf<String, URI?>()
    links["authenticate"] =
      authObject.links.find { link -> link.relation == "authenticate" }?.hrefURI
    links["api"] =
      authObject.links.find { link -> link.relation == "api" }?.hrefURI
    links["ekirjasto_token"] =
      authObject.links.find { link -> link.relation == "ekirjasto_token" }?.hrefURI
    links["magazine_service"] =
      authObject.links.find { link -> link.relation == "magazine_service" }?.hrefURI
    links["tunnistus_start"] =
      authObject.links.find { link -> link.relation == "tunnistus_start" }?.hrefURI
    links["tunnistus_finish"] =
      authObject.links.find { link -> link.relation == "tunnistus_finish" }?.hrefURI
    links["passkey_login_start"] =
      authObject.links.find { link -> link.relation == "passkey_login_start" }?.hrefURI
    links["passkey_login_finish"] =
      authObject.links.find { link -> link.relation == "passkey_login_finish" }?.hrefURI
    links["passkey_register_start"] =
      authObject.links.find { link -> link.relation == "passkey_register_start" }?.hrefURI
    links["passkey_register_finish"] =
      authObject.links.find { link -> link.relation == "passkey_register_finish" }?.hrefURI
    links["relations"] =
      authObject.links.find { link -> link.relation == "relations" }?.hrefURI
    links["invite"] =
      authObject.links.find { link -> link.relation == "invite" }?.hrefURI

    val emptyLinks = mutableListOf<String>()
    for ((name, link) in links) {
      if (link == null) {
        emptyLinks.add(name)
      }
    }
    if (emptyLinks.isNotEmpty()) {
      val message = "Missing value for links: $emptyLinks."
      taskRecorder.currentStepFailed(message, authDocumentUnusable(this.description))
      throw IOException(message)
    }

    return AccountProviderAuthenticationDescription.Ekirjasto(
      authenticate = links["authenticate"]!!,
      description = authObject.description,
      api = links["api"]!!,
      ekirjasto_token = links["ekirjasto_token"]!!,
      magazine_service = links["magazine_service"]!!,
      tunnistus_start = links["tunnistus_start"]!!,
      tunnistus_finish = links["tunnistus_finish"]!!,
      passkey_login_start = links["passkey_login_start"]!!,
      passkey_login_finish = links["passkey_login_finish"]!!,
      passkey_register_start = links["passkey_register_start"]!!,
      passkey_register_finish = links["passkey_register_finish"]!!,
      relations = links["relations"]!!,
      invite = links["invite"]!!
    )
  }

  private fun extractAuthenticationDescriptionSAML20(
    taskRecorder: TaskRecorderType,
    authObject: AuthenticationObject
  ): AccountProviderAuthenticationDescription {
    val authenticate =
      authObject.links.find { link -> link.relation == "authenticate" }
    val logo =
      authObject.links.find { link -> link.relation == "logo" }

    val authenticateURI = authenticate?.hrefURI
    if (authenticateURI == null) {
      val message = this.stringResources.resolvingAuthDocumentSAML20Malformed
      taskRecorder.currentStepFailed(message, authDocumentUnusable(this.description))
      throw IOException(message)
    }

    return AccountProviderAuthenticationDescription.SAML2_0(
      authenticate = authenticateURI,
      description = authObject.description,
      logoURI = logo?.hrefURI
    )
  }

  private fun extractAuthenticationDescriptionOAuthIntermediary(
    taskRecorder: TaskRecorderType,
    authObject: AuthenticationObject
  ): AccountProviderAuthenticationDescription {
    val authenticate =
      authObject.links.find { link -> link.relation == "authenticate" }
    val logo =
      authObject.links.find { link -> link.relation == "logo" }

    val authenticateURI = authenticate?.hrefURI
    if (authenticateURI == null) {
      val message = this.stringResources.resolvingAuthDocumentOAuthMalformed
      taskRecorder.currentStepFailed(message, authDocumentUnusable(this.description))
      throw IOException(message)
    }

    return AccountProviderAuthenticationDescription.OAuthWithIntermediary(
      authenticate = authenticateURI,
      description = authObject.description,
      logoURI = logo?.hrefURI
    )
  }

  private fun extractAuthenticationDescriptionBasic(
    authObject: AuthenticationObject
  ): AccountProviderAuthenticationDescription.Basic {
    val loginRestrictions =
      authObject.inputs[LABEL_LOGIN]
    val passwordRestrictions =
      authObject.inputs[LABEL_PASSWORD]
    val logo =
      authObject.links.find { link -> link.relation == "logo" }
        ?.hrefURI

    return AccountProviderAuthenticationDescription.Basic(
      barcodeFormat = loginRestrictions?.barcodeFormat,
      description = authObject.description,
      keyboard = this.parseKeyboardType(loginRestrictions?.keyboardType),
      labels = authObject.labels,
      logoURI = logo,
      passwordKeyboard = this.parseKeyboardType(passwordRestrictions?.keyboardType),
      passwordMaximumLength = passwordRestrictions?.maximumLength ?: 0
    )
  }

  private fun extractAuthenticationDescriptionBasicToken(
    taskRecorder: TaskRecorderType,
    authObject: AuthenticationObject
  ): AccountProviderAuthenticationDescription.BasicToken {
    val authenticate =
      authObject.links.find { link -> link.relation == "authenticate" }

    val authenticateURI = authenticate?.hrefURI
    if (authenticateURI == null) {
      val message = this.stringResources.resolvingAuthDocumentBasicTokenMalformed
      taskRecorder.currentStepFailed(message, authDocumentUnusable(this.description))
      throw IOException(message)
    }

    val loginRestrictions =
      authObject.inputs[LABEL_LOGIN]
    val passwordRestrictions =
      authObject.inputs[LABEL_PASSWORD]
    val logo =
      authObject.links.find { link -> link.relation == "logo" }
        ?.hrefURI

    return AccountProviderAuthenticationDescription.BasicToken(
      authenticationURI = authenticateURI,
      barcodeFormat = loginRestrictions?.barcodeFormat,
      description = authObject.description,
      keyboard = this.parseKeyboardType(loginRestrictions?.keyboardType),
      labels = authObject.labels,
      logoURI = logo,
      passwordKeyboard = this.parseKeyboardType(passwordRestrictions?.keyboardType),
      passwordMaximumLength = passwordRestrictions?.maximumLength ?: 0
    )
  }

  private fun parseKeyboardType(
    text: String?
  ): KeyboardInput {
    if (text == null) {
      return KeyboardInput.DEFAULT
    }

    return try {
      KeyboardInput.valueOf(
        text.uppercase(Locale.ROOT).replace(' ', '_')
      )
    } catch (e: Exception) {
      this.logger.error("unable to interpret keyboard type: {}", text)
      KeyboardInput.DEFAULT
    }
  }

  private fun extractAuthenticationDescriptionCOPPA(
    taskRecorder: TaskRecorderType,
    authObject: AuthenticationObject
  ): AccountProviderAuthenticationDescription.COPPAAgeGate {
    val under13 = authObject.links.find { link ->
      link.relation == "http://librarysimplified.org/terms/rel/authentication/restriction-not-met"
    }?.hrefURI
    val over13 = authObject.links.find { link ->
      link.relation == "http://librarysimplified.org/terms/rel/authentication/restriction-met"
    }?.hrefURI

    return if (under13 != null && over13 != null) {
      AccountProviderAuthenticationDescription.COPPAAgeGate(
        greaterEqual13 = over13,
        under13 = under13
      )
    } else {
      val message = this.stringResources.resolvingAuthDocumentCOPPAAgeGateMalformed
      taskRecorder.currentStepFailed(message, authDocumentUnusable(this.description))
      throw IOException(message)
    }
  }

  private fun fetchAuthDocument(
    taskRecorder: TaskRecorderType,
    onProgress: AccountProviderResolutionListenerType
  ): AuthenticationDocument? {
    this.logger.debug("fetching authentication document")
    taskRecorder.beginNewStep(this.stringResources.resolvingAuthDocument)
    onProgress.invoke(this.description.id, this.stringResources.resolvingAuthDocument)

    val targetLink = this.description.authenticationDocumentURI
    if (targetLink == null) {
      this.logger.debug("description did not contain an authentication document link")
      return null
    }

    return when (targetLink) {
      is Link.LinkBasic -> {
        val request =
          this.http.newRequest(targetLink.href)
            .build()

        val result = request.execute()
        taskRecorder.addAttribute("Authentication Document", targetLink.href.toString())
        taskRecorder.addAttributes(result.status.properties?.problemReport?.toMap() ?: emptyMap())

        when (val status = result.status) {
          is LSHTTPResponseStatus.Responded.OK -> {
            this.parseAuthenticationDocument(
              targetURI = targetLink.href,
              stream = status.bodyStream ?: emptyStream(),
              taskRecorder = taskRecorder
            )
          }

          is LSHTTPResponseStatus.Responded.Error -> {
            if (MIMECompatibility.isCompatibleStrictWithoutAttributes(
                status.properties.contentType,
                authDocumentType
              )
            ) {
              this.parseAuthenticationDocument(
                targetURI = targetLink.href,
                stream = status.bodyStream ?: emptyStream(),
                taskRecorder = taskRecorder
              )
            } else {
              val message = this.stringResources.resolvingAuthDocumentRetrievalFailed
              taskRecorder.currentStepFailed(
                message,
                httpRequestFailed(
                  targetLink.hrefURI,
                  status.properties.originalStatus,
                  status.properties.message
                )
              )
              throw IOException(message)
            }
          }

          is LSHTTPResponseStatus.Failed -> {
            throw IOException(status.exception)
          }
        }
      }

      is Link.LinkTemplated -> {
        val message = this.stringResources.resolvingAuthDocumentUnusableLink
        taskRecorder.currentStepFailed(message, authDocumentUnusableLink(this.description))
        throw IOException(message)
      }
    }
  }

  private fun emptyStream() = ByteArrayInputStream(ByteArray(0))

  private fun parseAuthenticationDocument(
    targetURI: URI,
    stream: InputStream,
    taskRecorder: TaskRecorderType
  ): AuthenticationDocument {
    this.logger.debug("parsing authentication document {}", targetURI)
    return this.authDocumentParsers.createParser(targetURI, stream).use { parser ->
      when (val parseResult = parser.parse()) {
        is ParseResult.Success -> {
          parseResult.warnings.forEach { warning -> this.logger.warn("{}", warning.message) }
          parseResult.result
        }

        is ParseResult.Failure -> {
          parseResult.warnings.forEach { warning -> this.logger.warn("{}", warning.message) }
          parseResult.errors.forEach { error -> this.logger.error("{}", error.message) }
          val message = this.stringResources.resolvingAuthDocumentParseFailed
          taskRecorder.currentStepFailed(message, authDocumentParseFailed(this.description))
          throw IOException(message)
        }
      }
    }
  }
}
