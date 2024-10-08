package org.nypl.simplified.accounts.api

import java.net.URI

/**
 * A set of account credentials.
 */

sealed class AccountAuthenticationCredentials {

  /**
   * @return The current credentials without any post-activation Adobe credentials
   */

  abstract fun withoutAdobePostActivationCredentials(): AccountAuthenticationCredentials

  /**
   * @return The current credentials without any post-activation Adobe credentials
   */

  abstract fun withAdobePreActivationCredentials(
    newCredentials: AccountAuthenticationAdobePreActivationCredentials
  ): AccountAuthenticationCredentials

  /**
   * The Adobe DRM credentials available to the account.
   */

  abstract val adobeCredentials: AccountAuthenticationAdobePreActivationCredentials?

  /**
   * The description of authentication method that was used. This corresponds to the [AccountProviderAuthenticationDescription.description] field.
   */

  abstract val authenticationDescription: String?

  /**
   * The annotations URI for the account. This is used to send analytics events to the
   * Circulation Manager if the feature is enabled for the account.
   */

  abstract val annotationsURI: URI?

  /**
   * The device registration URI for the account. This is used to register or unregister a device
   * to start/stop receiving push notifications.
   */

  abstract val deviceRegistrationURI: URI?

  /**
   * The user used basic authentication to authenticate.
   */

  data class Basic(
    val userName: AccountUsername,
    val password: AccountPassword,
    override val adobeCredentials: AccountAuthenticationAdobePreActivationCredentials?,
    override val authenticationDescription: String?,
    override val annotationsURI: URI?,
    override val deviceRegistrationURI: URI?
  ) : AccountAuthenticationCredentials() {
    override fun withoutAdobePostActivationCredentials(): AccountAuthenticationCredentials {
      return this.copy(
        adobeCredentials = this.adobeCredentials?.copy(postActivationCredentials = null)
      )
    }

    override fun withAdobePreActivationCredentials(
      newCredentials: AccountAuthenticationAdobePreActivationCredentials
    ): AccountAuthenticationCredentials {
      return this.copy(adobeCredentials = newCredentials)
    }
  }

  /**
   * The user used basic token authentication to authenticate.
   */

  data class BasicToken(
    val userName: AccountUsername,
    val password: AccountPassword,
    val authenticationTokenInfo: AccountAuthenticationTokenInfo,
    override val adobeCredentials: AccountAuthenticationAdobePreActivationCredentials?,
    override val authenticationDescription: String?,
    override val annotationsURI: URI?,
    override val deviceRegistrationURI: URI?
  ) : AccountAuthenticationCredentials() {
    override fun withoutAdobePostActivationCredentials(): AccountAuthenticationCredentials {
      return this.copy(
        adobeCredentials = this.adobeCredentials?.copy(postActivationCredentials = null)
      )
    }

    override fun withAdobePreActivationCredentials(
      newCredentials: AccountAuthenticationAdobePreActivationCredentials
    ): AccountAuthenticationCredentials {
      return this.copy(adobeCredentials = newCredentials)
    }

    fun updateAccessToken(accessToken: String?): BasicToken {
      return if (!accessToken.isNullOrBlank()) {
        this.copy(
          authenticationTokenInfo = authenticationTokenInfo.copy(
            accessToken = accessToken
          )
        )
      } else {
        this
      }
    }
  }

  /**
   * The user used OAuth (with an intermediary) authentication to authenticate.
   */

  data class OAuthWithIntermediary(
    val accessToken: String,
    override val adobeCredentials: AccountAuthenticationAdobePreActivationCredentials?,
    override val authenticationDescription: String?,
    override val annotationsURI: URI?,
    override val deviceRegistrationURI: URI?
  ) : AccountAuthenticationCredentials() {
    override fun withoutAdobePostActivationCredentials(): AccountAuthenticationCredentials {
      return this.copy(
        adobeCredentials = this.adobeCredentials?.copy(postActivationCredentials = null)
      )
    }

    override fun withAdobePreActivationCredentials(
      newCredentials: AccountAuthenticationAdobePreActivationCredentials
    ): AccountAuthenticationCredentials {
      return this.copy(adobeCredentials = newCredentials)
    }
  }

  /**
   * The user used SAML 2.0 authentication to authenticate.
   */

  data class SAML2_0(
    val accessToken: String,
    val patronInfo: String,
    val cookies: List<AccountCookie>,
    override val adobeCredentials: AccountAuthenticationAdobePreActivationCredentials?,
    override val authenticationDescription: String?,
    override val annotationsURI: URI?,
    override val deviceRegistrationURI: URI?
  ) : AccountAuthenticationCredentials() {
    override fun withoutAdobePostActivationCredentials(): AccountAuthenticationCredentials {
      return this.copy(
        adobeCredentials = this.adobeCredentials?.copy(postActivationCredentials = null)
      )
    }

    override fun withAdobePreActivationCredentials(
      newCredentials: AccountAuthenticationAdobePreActivationCredentials
    ): AccountAuthenticationCredentials {
      return this.copy(adobeCredentials = newCredentials)
    }
  }

  /**
   * The user used E-kirjasto authentication to authenticate.
   */

  data class Ekirjasto(
    val accessToken: String,
    val ekirjastoToken: String?,
    val patronPermanentID: String?,
    override val adobeCredentials: AccountAuthenticationAdobePreActivationCredentials?,
    override val authenticationDescription: String?,
    override val annotationsURI: URI?,
    override val deviceRegistrationURI: URI?
  ) : AccountAuthenticationCredentials() {
    override fun withoutAdobePostActivationCredentials(): AccountAuthenticationCredentials {
      return this.copy(
        adobeCredentials = this.adobeCredentials?.copy(postActivationCredentials = null)
      )
    }

    override fun withAdobePreActivationCredentials(
      newCredentials: AccountAuthenticationAdobePreActivationCredentials
    ): AccountAuthenticationCredentials {
      return this.copy(adobeCredentials = newCredentials)
    }
  }
}
