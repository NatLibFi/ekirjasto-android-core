package org.nypl.simplified.ui.accounts.ekirjastosuomifi

import android.app.Application
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.ViewModel
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import hu.akarnokd.rxjava2.subjects.UnicastWorkSubject
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.PublishSubject
import org.librarysimplified.services.api.Services
import org.nypl.simplified.accounts.api.AccountCookie
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.webview.WebViewUtilities
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.librarysimplified.ui.accounts.R

/**
 * View state for the E-kirjasto fragment.
 */

class AccountEkirjastoSuomiFiViewModel(
  private val application: Application,
  private val account: AccountID,
  private val description: AccountProviderAuthenticationDescription.Ekirjasto,
) : ViewModel() {

  private val logger =
    LoggerFactory.getLogger(AccountEkirjastoSuomiFiViewModel::class.java)

  private val resources: Resources =
    application.resources

  private val webViewDataDir: File =
    this.application.getDir("webview", Context.MODE_PRIVATE)

  private val eventSubject =
    PublishSubject.create<AccountEkirjastoSuomiFiInternalEvent>()

  private val authInfo =
    AtomicReference<AuthInfo>()

  private val services =
    Services.serviceDirectory()

  private val buildConfig =
    services.requireService(BuildConfigurationServiceType::class.java)

  private val profilesController =
    services.requireService(ProfilesControllerType::class.java)

  private val subscriptions = CompositeDisposable(
    eventSubject
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(
        { event -> this.events.onNext(event) },
        { error -> this.events.onError(error) },
        { this.events.onComplete() }
      )
  )

  private data class AuthInfo(
    val token: String,
    val exp: Long,
    val cookies: List<AccountCookie>
  )

  private class AccountEkirjastoSuomiFiWebClient(
    private val logger: Logger,
    private val resources: Resources,
    private val eventSubject: PublishSubject<AccountEkirjastoSuomiFiInternalEvent>,
    private val authInfo: AtomicReference<AuthInfo>,
    private val profiles: ProfilesControllerType,
    private val account: AccountID,
    private val webViewDataDir: File,
    private val description: AccountProviderAuthenticationDescription.Ekirjasto
  ) : WebViewClient() {

    var isReady = false

    init {
      /*
       * The web view may be harboring session cookies that are still valid, which could make the
       * login page go straight through to the success redirect when loaded. Since we're trying to
       * do a fresh log in, we need to make sure existing session cookies are not sent. We don't
       * know which cookies are which, so they all need to be removed.
       */

      CookieManager.getInstance().removeAllCookies {
        isReady = true

        this.eventSubject.onNext(
          AccountEkirjastoSuomiFiInternalEvent.WebViewClientReady()
        )
      }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
      logger.debug("onPageStarted $url")
      url?.let {
        if (it.startsWith(this.description.tunnistus_finish.toString())) {
          logger.debug("sending accessTokenStartReceive event")
          this.eventSubject.onNext(AccountEkirjastoSuomiFiInternalEvent.AccessTokenStartReceive())
        }
      }
      super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
      logger.debug("onPageFinished $url")
      url?.let {
        if (it.startsWith(this.description.tunnistus_finish.toString())) {
          view?.evaluateJavascript(
            "(function() {return document.querySelector('pre').innerText; })();"
          ) { json -> parseAuthToken(json.trim()
            .removePrefix("\"")
            .removeSuffix("\"")
            .replace("\\",""))
          }
        }
      }
      super.onPageFinished(view, url)
    }

    private fun parseAuthToken(
      json: String
    ) {

      logger.debug("Parsing authentication data from result")

      val mapper = ObjectMapper()
      val jsonNode = mapper.readTree(json)

      val ekirjastoToken = jsonNode["token"].asText();
      val exp = jsonNode.get("exp").asLong()

      val cookies = WebViewUtilities.dumpCookiesAsAccountCookies(
        CookieManager.getInstance(),
        this.webViewDataDir
      )

      if (ekirjastoToken == null) {
        val message = this.resources.getString(R.string.accountEkirjastoSuomiFiNoAccessToken)
        this.logger.error("{}", message)
        this.eventSubject.onNext(AccountEkirjastoSuomiFiInternalEvent.Failed(message))
        return
      }

      this.logger.debug("obtained ekirjasto token")
      this.authInfo.set(
        AuthInfo(
          token = ekirjastoToken,
          exp = exp,
          cookies = cookies
        )
      )

      this.profiles.profileAccountLogin(
        ProfileAccountLoginRequest.EkirjastoComplete(
          accountId = this.account,
          description = this.description,
          ekirjastoToken = ekirjastoToken,
          email = null
        )
      )
      this.eventSubject.onNext(
        AccountEkirjastoSuomiFiInternalEvent.AccessTokenObtained(
          token = ekirjastoToken,
          cookies = cookies
        )
      )
    }
  }

  override fun onCleared() {
    super.onCleared()

    if (this.authInfo.get() == null) {
      this.logger.debug("no access token obtained; cancelling login")
      this.profilesController.profileAccountLogin(
        ProfileAccountLoginRequest.EkirjastoCancel(
          accountId = this.account,
          description = this.description,
          email = null
        )
      )
    }

    this.eventSubject.onComplete()
    subscriptions.clear()
  }

  val webViewClient: WebViewClient =
    AccountEkirjastoSuomiFiWebClient(
      account = this.account,
      eventSubject = this.eventSubject,
      logger = this.logger,
      profiles = this.profilesController,
      resources = this.resources,
      authInfo = this.authInfo,
      webViewDataDir = this.webViewDataDir,
      description = this.description
    )

  val isWebViewClientReady: Boolean
    get() = (this.webViewClient as AccountEkirjastoSuomiFiWebClient).isReady

  val events: UnicastWorkSubject<AccountEkirjastoSuomiFiInternalEvent> =
    UnicastWorkSubject.create()

  val supportEmailAddress: String =
    buildConfig.supportErrorReportEmailAddress
}