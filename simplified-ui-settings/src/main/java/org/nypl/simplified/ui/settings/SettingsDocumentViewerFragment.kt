package org.nypl.simplified.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import org.nypl.simplified.android.ktx.supportActionBar
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.listeners.api.fragmentListeners
import org.librarysimplified.ui.settings.databinding.SettingsDocumentViewerBinding
import org.nypl.simplified.webview.WebViewUtilities
import org.thepalaceproject.theme.core.PalaceToolbar

class SettingsDocumentViewerFragment : Fragment() {

  private lateinit var binding: SettingsDocumentViewerBinding
  private lateinit var toolbar: PalaceToolbar

  private val listener: FragmentListenerType<SettingsDocumentViewerEvent> by fragmentListeners()
  private val title by lazy { arguments?.getString(TITLE_ID) }
  private val url by lazy { arguments?.getString(URL_ID) }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    binding = SettingsDocumentViewerBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    this.toolbar =
      view.rootView.findViewWithTag(PalaceToolbar.palaceToolbarName)

    if (!url.isNullOrBlank()) {
      binding.documentViewerWebView.let { webView ->
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

        // Beware that this line is a potential XSS vulnerability. The sites displayed are all
        // assumed to be benign enough for this to be OK and if the worst should happen the certs
        // can be revoked and this has been tested to work as expected.
        webView.settings.javaScriptEnabled = true
        // Disable file access to prevent potential file theft
        webView.settings.allowFileAccess = false

        WebViewUtilities.setForcedDark(webView.settings, resources.configuration)

        webView.loadUrl(url!!)
      }
    }
  }

  override fun onStart() {
    super.onStart()
    this.configureToolbar()
  }

  private fun configureToolbar() {
    val actionBar = this.supportActionBar ?: return
    actionBar.show()
    actionBar.setDisplayHomeAsUpEnabled(true)
    actionBar.setHomeActionContentDescription(null)
    actionBar.setTitle(this@SettingsDocumentViewerFragment.title)
    this.toolbar.setLogoOnClickListener {
      this.listener.post(SettingsDocumentViewerEvent.GoUpwards)
    }
  }

  companion object {
    private const val TITLE_ID = "org.nypl.simplified.ui.settings.SettingsDocumentViewerFragment.title"
    private const val URL_ID = "org.nypl.simplified.ui.settings.SettingsDocumentViewerFragment.url"

    fun create(title: String, url: String) = SettingsDocumentViewerFragment().apply {
      arguments = bundleOf(TITLE_ID to title, URL_ID to url)
    }
  }
}
