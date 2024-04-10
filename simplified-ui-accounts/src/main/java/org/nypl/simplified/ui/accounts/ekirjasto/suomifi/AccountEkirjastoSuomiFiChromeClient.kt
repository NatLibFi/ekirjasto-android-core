package org.nypl.simplified.ui.accounts.ekirjasto.suomifi

import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.ProgressBar

/**
 * A simple chrome client that shows/hides a progress bar as the web view loads pages.
 */

class AccountEkirjastoSuomiFiChromeClient(
  private val progress: ProgressBar
) : WebChromeClient() {

  override fun onProgressChanged(
    view: WebView,
    newProgress: Int
  ) {
    super.onProgressChanged(view, newProgress)

    if (newProgress >= 90) {
      this.progress.visibility = View.GONE
      view.visibility = View.VISIBLE
    } else {
      this.progress.visibility = View.VISIBLE
      view.visibility = View.INVISIBLE
    }
  }
}
