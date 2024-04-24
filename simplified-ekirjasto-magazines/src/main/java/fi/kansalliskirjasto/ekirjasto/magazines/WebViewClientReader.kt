package fi.kansalliskirjasto.ekirjasto.magazines

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.slf4j.LoggerFactory


/**
 * WebViewClient for reading a magazine.
 */
class WebViewClientReader(
  private val fragment: MagazinesFragment
) : WebViewClient() {
  private val logger = LoggerFactory.getLogger(this.javaClass)

  override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
    logger.debug("shouldOverrideUrlLoading()")
    val requestUri = request.url
    MagazinesFragment.latestUrl = requestUri
    logger.debug("Saved latest browser URL: {}", MagazinesFragment.latestUrl.toString())
    val path: String = requestUri.path!!
    if (!path.startsWith("/read")) {
      fragment.closeReader()
      return true
    }

    return false
  }
}
