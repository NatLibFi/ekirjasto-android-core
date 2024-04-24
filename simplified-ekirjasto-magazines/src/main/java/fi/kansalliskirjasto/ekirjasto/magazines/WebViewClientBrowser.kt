package fi.kansalliskirjasto.ekirjasto.magazines

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.slf4j.LoggerFactory


/**
 * WebViewClient for browsing a selection of magazines.
 */
class WebViewClientBrowser(
  private val fragment: MagazinesFragment
) : WebViewClient() {
  private val logger = LoggerFactory.getLogger(this.javaClass)

  /**
   * Watch for URL updates and run the web app's login method on demand
   */
  override fun doUpdateVisitedHistory(
    view: WebView,
    url: String,
    isReload: Boolean
  ) {
    logger.debug("doUpdateVisitedHistory()")
    val requestUri = Uri.parse(url)
    val path = requestUri.path!!
    logger.debug("path: {}", path)
    if ((path == "/") && (MagazinesFragment.latestUrl != null) && (!MagazinesFragment.latestUrl!!.path!!.startsWith("/read"))) {
      view.loadUrl(MagazinesFragment.latestUrl.toString())
      MagazinesFragment.latestUrl = null
      logger.debug("Cleared latest browser URL")
    }
    else if (path.startsWith("/unauthorized")) {
      val token = fragment.token
      logger.debug("token: {}", token)
      if (token != null) {
        view.evaluateJavascript("__ewl('login', {\"token\":\"$token\"});", null)
      }
      else {
        logger.warn("Token is null, cannot authenticate with magazine service")
      }
    }
  }

  /**
   * Block requests for the reader.
   */
  override fun shouldOverrideUrlLoading(
    view: WebView,
    request: WebResourceRequest
  ): Boolean {
    logger.debug("shouldOverrideUrlLoading()")
    val requestUri = request.url
    val path = requestUri.path!!
    logger.debug("path: {}", path)
    if (path.startsWith("/read")) {
      fragment.openReader(requestUri)
      return true
    }

    return false
  }
}
