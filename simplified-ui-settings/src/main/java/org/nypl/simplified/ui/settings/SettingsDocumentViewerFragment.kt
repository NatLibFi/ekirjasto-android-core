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
import org.nypl.simplified.ui.neutrality.NeutralToolbar
import org.nypl.simplified.ui.settings.databinding.SettingsDocumentViewerBinding

class SettingsDocumentViewerFragment : Fragment() {

  private lateinit var binding: SettingsDocumentViewerBinding
  private lateinit var toolbar: NeutralToolbar

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
      view.rootView.findViewWithTag(NeutralToolbar.neutralToolbarName)

    if (!url.isNullOrBlank()) {
      binding.documentViewerWebView.webViewClient = WebViewClient()
      binding.documentViewerWebView.webChromeClient = WebChromeClient()
      binding.documentViewerWebView.loadUrl(url)
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