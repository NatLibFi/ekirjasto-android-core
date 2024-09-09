package fi.kansalliskirjasto.ekirjasto.magazines

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.WebView
import android.widget.ProgressBar
import android.widget.RelativeLayout
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import fi.ekirjasto.magazines.R
import fi.kansalliskirjasto.ekirjasto.util.LanguageUtil
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.services.api.Services
import org.nypl.simplified.android.ktx.supportActionBar
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.listeners.api.fragmentListeners
import org.slf4j.LoggerFactory
import org.thepalaceproject.theme.core.PalaceToolbar


/**
 * A fragment for browsing and reading digital magazines.
 */
@SuppressLint("SetJavaScriptEnabled", "SourceLockedOrientationActivity")
class MagazinesFragment : Fragment(R.layout.magazines) {
  companion object {
    private const val PARAMETERS_ID =
      "fi.kansalliskirjasto.ekirjasto.magazines.MagazinesFragment.parameters"

    // TODO: Move to MagazinesArguments
    var latestUrl: Uri? = null

    /**
     * Create a magazines fragment for the given parameters.
     */
    fun create(parameters: MagazinesArguments): MagazinesFragment {
      val fragment = MagazinesFragment()
      fragment.arguments = bundleOf(PARAMETERS_ID to parameters)
      return fragment
    }
  }

  private val logger = LoggerFactory.getLogger(MagazinesFragment::class.java)

  private val parameters: MagazinesArguments by lazy {
    requireArguments()[PARAMETERS_ID] as MagazinesArguments
  }

  private val services = Services.serviceDirectory()
  private val http =
    services.requireService(LSHTTPClientType::class.java)
  private val configurationService =
    services.requireService(BuildConfigurationServiceType::class.java)

  private val listener: FragmentListenerType<MagazinesEvent> by fragmentListeners()

  private val viewModel: MagazinesViewModel by viewModels(
    factoryProducer = {
      MagazinesViewModelFactory(
        application = requireActivity().application,
        services = services,
        arguments = parameters,
        listener = listener
      )
    }
  )

  val token: String?
    get() = viewModel.token

  private lateinit var toolbar: PalaceToolbar
  private lateinit var loadingLayout: ViewGroup
  private lateinit var loadingProgressBar: ProgressBar
  private lateinit var signInPromptLayout: ViewGroup
  private lateinit var browsingLayout: ViewGroup
  private lateinit var browsingWebView: WebView
  private var readerDialog: Dialog? = null
  private var readerWebView: WebView? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    logger.debug("onCreate (recreating: {})", savedInstanceState != null)
    super.onCreate(savedInstanceState)
  }

  override fun onStart() {
    logger.debug("onStart")
    super.onStart()
  }

  override fun onStop() {
    logger.debug("onStop")
    if (readerDialog == null && browsingWebView.url != null) {
      latestUrl = Uri.parse(browsingWebView.url)
      logger.debug("Saved latest browser URL: {}", latestUrl.toString())
    }
    readerDialog?.dismiss()
    requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    super.onStop()
  }

  override fun onDestroyView() {
    logger.debug("onDestroyView")
    readerWebView?.destroy()
    readerWebView = null
    super.onDestroyView()
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    logger.debug("onViewCreated (recreating: {})", savedInstanceState != null)
    logger.debug("savedInstanceState: {}", savedInstanceState)
    if (savedInstanceState != null) {
      logger.debug("latestUrl: {}", latestUrl)
    }
    super.onViewCreated(view, savedInstanceState)

    toolbar = view.rootView.findViewWithTag(PalaceToolbar.palaceToolbarName)
    configureToolbar(showToolbar = true)

    loadingLayout = view.findViewById(R.id.magazinesLoadingLayout)
    loadingProgressBar = loadingLayout.findViewById(R.id.magazinesLoadingProgressBar)
    signInPromptLayout = view.findViewById(R.id.magazinesSignInPromptLayout)
    browsingLayout = view.findViewById(R.id.magazinesBrowsingLayout)
    browsingWebView = browsingLayout.findViewById(R.id.magazinesBrowsingWebView)
    configureBrowsingWebView()

    // UI state changes will call reconfigureUI
    viewModel.stateLive.observe(viewLifecycleOwner, this::reconfigureUI)

    if (viewModel.token == null) {
      // Start fetching the token (asynchronously, the result will update UI state)
      viewModel.fetchTokenAsync()
    }
  }

  /**
   * Open the magazine reader with the given URL.
   */
  fun openReader(url: Uri) {
    logger.debug("openReader({})", url)
    latestUrl = url
    logger.debug("Saved latest reader URL: {}", latestUrl.toString())

    // Set up WebView for reading the magazine
    readerWebView?.destroy()
    readerWebView = WebView(requireContext())
    readerWebView!!.getSettings().javaScriptEnabled = true
    readerWebView!!.getSettings().userAgentString = http.userAgent()
    // TODO: Move color to ekirjasto-theme or to the module's colors.xml
    readerWebView!!.setBackgroundColor(Color.parseColor("#1a1d21"))
    val webViewClientReader = WebViewClientReader(this)
    readerWebView!!.setWebViewClient(webViewClientReader)
    readerWebView!!.loadUrl(url.toString())

    // Create a fullscreen (excluding system top and bottom bars) dialog to hold the reader
    readerDialog = Dialog(requireContext(), R.style.Theme_EkirjastoReaderWebView)
    val window: Window = readerDialog!!.window!!
    window.attributes.windowAnimations = android.R.style.Animation_Dialog
    //val wic = WindowInsetsControllerCompat(window, window.decorView)
    //wic.isAppearanceLightStatusBars = true
    // Set status bar color
    window.statusBarColor = ContextCompat.getColor(
      requireContext(),
      org.thepalaceproject.theme.core.R.color.PalaceStatusBarColor
    )
    val paramsWebView = RelativeLayout.LayoutParams(
      RelativeLayout.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    readerDialog!!.addContentView(readerWebView!!, paramsWebView)
    readerDialog!!.setOnDismissListener { onReaderClosed() }
    readerDialog!!.show()
  }

  /**
   * Close the magazine reader.
   */
  fun closeReader() {
    logger.debug("closeReader()")
    readerDialog?.dismiss()
    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
  }

  /**
   * Called when the magazine reader is closed (dialog is dismissed/closed).
   */
  private fun onReaderClosed() {
    logger.debug("onReaderClosed()")
    readerWebView?.destroy()
    readerWebView = null
    readerDialog = null
    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
  }

  private fun getBrowsingUrlBase(): String? {
    val magazineServiceUrl = viewModel.getMagazineServiceUrl() ?: return null
    // Get the language to use and load the magazine collection browser
    val language = LanguageUtil.getUserLanguage(this.requireContext())
    return "$magazineServiceUrl/$language"
  }

  /**
   * Setup the browsing WebView (does not set the URL).
   */
  private fun configureBrowsingWebView() {
    // Set up WebView for browsing the magazine collection
    browsingWebView.getSettings().javaScriptEnabled = true
    browsingWebView.getSettings().userAgentString = http.userAgent()
    val webViewClient = WebViewClientBrowser(this)
    browsingWebView.setWebViewClient(webViewClient)

    // Handle back presses in the WebView, until it can't go back anymore
    val activity = requireActivity() as AppCompatActivity
    activity.onBackPressedDispatcher.addCallback(this) {
      if (browsingWebView.url != null
          && browsingWebView.canGoBack()
          && Uri.parse(browsingWebView.url).path != "/") {
        browsingWebView.goBack()
      }
      else {
        try {
          isEnabled = false
          activity.onBackPressed()
        }
        finally {
          isEnabled = true
        }
      }
    }
  }

  /**
   * Configure the top toolbar (only shown when the magazine browser is hidden).
   *
   * @param showToolbar  Whether the toolbar should be shown or hidden.
   */
  private fun configureToolbar(showToolbar: Boolean) {
    try {
      toolbar.title = getString(R.string.magazinesHeader)
      if (showToolbar) {
        supportActionBar?.show()
      }
      else {
        supportActionBar?.hide()
      }

      // NOTE: Not checking `showChangeAccountsUi` or `showActionBarLogo` from
      // ConfigurationService here, since this module is only for E-kirjasto
      // Show the logo, but don't make it act like a button
      supportActionBar?.setHomeActionContentDescription(null)
      supportActionBar?.setLogo(configurationService.brandingAppIcon)
      supportActionBar?.setDisplayHomeAsUpEnabled(false)
      toolbar.setLogoOnClickListener{
        viewModel.goUpwards()
      }
    } catch (e: Exception) {
      // Nothing to do
    }
  }

  /**
   * Set the magazine browsing WebView's URL.
   */
  private fun startBrowsing(token: String? = null) {
    val urlBase = getBrowsingUrlBase()
      ?: throw java.lang.Exception("Could not find magazine service URL")
    logger.debug("startBrowsing({})", urlBase)
    val url =
      if (token == null) {
        urlBase
      }
      else {
        "$urlBase/login?token=$token"
      }
    browsingWebView.loadUrl(url)
  }

  private fun reconfigureUI(state: MagazinesState) {
    logger.debug("reconfigureUI({})", state)

    // Hide everything by default
    configureToolbar(showToolbar = false)
    loadingLayout.visibility = View.INVISIBLE
    signInPromptLayout.visibility = View.INVISIBLE
    browsingLayout.visibility = View.INVISIBLE

    when (state) {
      is MagazinesState.MagazinesLoading -> {
        logger.debug("Set state to loading")
        loadingLayout.visibility = View.VISIBLE
      }
      is MagazinesState.MagazinesBrowsing -> {
        logger.debug("Set state to browsing")
        startBrowsing(viewModel.token)
        browsingLayout.visibility = View.VISIBLE
        if (latestUrl != null && latestUrl?.path?.startsWith("/read")!!) {
          openReader(latestUrl!!)
        }
      }
      is MagazinesState.MagazinesReading -> {
        logger.debug("Set state to reading")
        browsingLayout.visibility = View.VISIBLE
      }
      is MagazinesState.MagazinesLoadFailed -> {
        logger.warn("Set state to failed")
        configureToolbar(showToolbar = true)
        signInPromptLayout.visibility = View.VISIBLE
      }
    }
  }
}
