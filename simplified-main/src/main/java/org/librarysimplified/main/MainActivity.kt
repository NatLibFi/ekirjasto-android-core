package org.librarysimplified.main

import android.Manifest
import android.app.ActionBar
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks
import com.google.firebase.dynamiclinks.PendingDynamicLinkData
import com.transifex.txnative.TxNative
import fi.kansalliskirjasto.ekirjasto.testing.ui.TestLoginFragment
import fi.kansalliskirjasto.ekirjasto.util.FontSizeUtil
import fi.kansalliskirjasto.ekirjasto.util.LocaleHelper
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.catalog.CatalogRefreshViewModel
import org.librarysimplified.ui.login.LoginMainFragment
import org.librarysimplified.ui.onboarding.OnboardingEvent
import org.librarysimplified.ui.onboarding.OnboardingFragment
import org.librarysimplified.ui.splash.SplashEvent
import org.librarysimplified.ui.splash.SplashFragment
import org.librarysimplified.ui.tutorial.TutorialEvent
import org.librarysimplified.ui.tutorial.TutorialFragment
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.deeplinks.controller.api.DeepLinksControllerType
import org.nypl.simplified.deeplinks.controller.api.ScreenID
import org.nypl.simplified.listeners.api.ListenerRepository
import org.nypl.simplified.listeners.api.listenerRepositories
import org.nypl.simplified.oauth.OAuthCallbackIntentParsing
import org.nypl.simplified.oauth.OAuthParseResult
import org.nypl.simplified.profiles.api.ProfilesDatabaseType.AnonymousProfileEnabled.ANONYMOUS_PROFILE_DISABLED
import org.nypl.simplified.profiles.api.ProfilesDatabaseType.AnonymousProfileEnabled.ANONYMOUS_PROFILE_ENABLED
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest.OAuthWithIntermediaryComplete
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.ui.accounts.ekirjasto.TextSizeEvent
import org.nypl.simplified.ui.announcements.TipsEvent
import org.nypl.simplified.ui.branding.BrandingSplashServiceType
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.ServiceLoader
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(R.layout.main_host) {

  companion object {
    private const val STATE_ACTION_BAR_IS_SHOWING = "ACTION_BAR_IS_SHOWING"
    private const val LOGIN_SCREEN_ID = "login"
  }

  private val logger = LoggerFactory.getLogger(MainActivity::class.java)
  private val listenerRepo: ListenerRepository<MainActivityListenedEvent, Unit> by listenerRepositories()
  private lateinit var refreshViewModel: CatalogRefreshViewModel
  private lateinit var fontSizeManager: FontSizeUtil

  private val defaultViewModelFactory: ViewModelProvider.Factory by lazy {
    MainActivityDefaultViewModelFactory(super.defaultViewModelProviderFactory)
  }

  override fun attachBaseContext(newBase: Context?) {
    //Create a new configuration based on the font size that has been set
    this.fontSizeManager = FontSizeUtil(newBase!!)
    val newConfig = Configuration(newBase.resources.configuration)
    newConfig.fontScale = fontSizeManager.getFontSize()

    //Create a new context based on newBase,but with the changed configuration
    //This method doesn't break Transifex
    val cont = newBase.createConfigurationContext(newConfig)
    //LocaleHelper is used to set into local memory the language choice
    super.attachBaseContext(LocaleHelper.onAttach(cont))
  }

  /**
   * Set the visible font size. Update is visible to user instantly.
   */
  private fun updateFontSize(fontSize: Float) {
    fontSizeManager.setFontSize(fontSize)
    //After setting the font size, we want to reload the activity so the changes show
    recreate()
  }



  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    this.logger.debug("onCreate (recreating {})", savedInstanceState != null)
    super.onCreate(savedInstanceState)
    this.logger.debug("onCreate (super completed)")

    // Get the viewModel that is used to communicate that we want to refresh our loan
    // and hold views
    refreshViewModel = ViewModelProvider(this).get(CatalogRefreshViewModel::class.java)

    interceptDeepLink()
    val toolbar: Toolbar = this.findViewById(R.id.mainToolbar)

    //Add insets so we don't have overlap with system bars
    ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
      val insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      // Apply the insets as a margin to the view
      view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        topMargin = insets.top
        leftMargin = insets.left
        rightMargin = insets.right
      }

      // Return CONSUMED since we don't want the window insets to keep passing
      // down to descendant views.
      WindowInsetsCompat.CONSUMED
    }
    this.setSupportActionBar(toolbar)
    this.supportActionBar?.setDisplayHomeAsUpEnabled(true)
    this.supportActionBar?.setDisplayShowHomeEnabled(true)
    this.supportActionBar?.hide() // Hide toolbar until requested

    if (savedInstanceState == null) {
      this.openSplashScreen()
    } else {
      if (savedInstanceState.getBoolean(STATE_ACTION_BAR_IS_SHOWING)) {
        this.supportActionBar?.show()
      } else {
        this.supportActionBar?.hide()
      }
    }

    askNotificationPermission()
    logger.debug("SHOW NOTIFICATION PERMISSION")
  }

  /**
   * Intercept deep links.
   */
  private fun interceptDeepLink() {
    logger.debug("interceptDeepLink()")
    val action: String? = intent?.action
    logger.debug("action: $action")
    val data: Uri? = intent?.data
    logger.debug("data: {}", data)

    if (data is Uri) {
      if (data.host == "test-login") {
        interceptDeepLinkTestLogin(data)
      }
      else {
        interceptDeepLinkBarcode()
      }
    }
  }

  /**
   * Intercept test login deep links.
   */
  private fun interceptDeepLinkTestLogin(data: Uri) {
    logger.debug("interceptDeepLinkTestLogin()")
    openTestLogin(
      prefilledUsername = data.getQueryParameter("username") ?: ""
    )
  }

  private fun setFontSize(event: TextSizeEvent) {
    return when (event) {
      TextSizeEvent.TextSizeSmall -> {
        updateFontSize(1.0f)
        this.logger.debug("TextSmall")
      }
      TextSizeEvent.TextSizeMedium -> {
        updateFontSize(1.25f)
        this.logger.debug("TextSmall")
      }
      TextSizeEvent.TextSizeLarge -> {
        updateFontSize(1.5f)
        this.logger.debug("TextMedium")
      }
      TextSizeEvent.TextSizeExtraLarge -> {
        updateFontSize(1.75f)
        this.logger.debug("TextLarge")
      }
      TextSizeEvent.TextSizeExtraExtraLarge -> {
        updateFontSize(2.0f)
        this.logger.debug("TextLarge")
      }
    }
  }

  // "Original" interceptDeepLink()
  private fun interceptDeepLinkBarcode() {
    val pendingLink =
      FirebaseDynamicLinks.getInstance()
        .getDynamicLink(intent)

    pendingLink.addOnFailureListener(this) { e ->
      this.logger.error("Failed to retrieve dynamic link: ", e)
    }

    pendingLink.addOnSuccessListener { linkData: PendingDynamicLinkData? ->
      val deepLink = linkData?.link
      if (deepLink == null) {
        this.logger.error("Pending deep link had no link field")
        return@addOnSuccessListener
      }

      val libraryID = deepLink.getQueryParameter("libraryid")
      if (libraryID == null) {
        this.logger.error("Pending deep link had no libraryid parameter.")
        return@addOnSuccessListener
      }

      val barcode = deepLink.getQueryParameter("barcode")
      if (barcode == null) {
        this.logger.error("Pending deep link had no barcode parameter.")
        return@addOnSuccessListener
      }

      val services =
        Services.serviceDirectory()
      val profiles =
        services.requireService(ProfilesControllerType::class.java)
      val deepLinksController =
        services.requireService(DeepLinksControllerType::class.java)

      val accountURI =
        URI("urn:uuid$libraryID")

      val accountResult =
        profiles.profileAccountCreate(accountURI)
          .get(3L, TimeUnit.MINUTES)

      // XXX: Creating an error report would be nice here.
      if (accountResult is TaskResult.Failure) {
        this.logger.error("Unable to create an account with ID {}: ", accountURI)
        return@addOnSuccessListener
      }

      val accountID =
        (accountResult as TaskResult.Success).result.id
      val screenRaw =
        deepLink.getQueryParameter("screen")

      val screenId: ScreenID =
        when (screenRaw) {
          null -> {
            this.logger.warn("Deep link did not have a screen parameter.")
            ScreenID.UNSPECIFIED
          }

          LOGIN_SCREEN_ID -> ScreenID.LOGIN
          else -> {
            this.logger.warn("Deep link had an unrecognized screen parameter {}.", screenRaw)
            ScreenID.UNSPECIFIED
          }
        }

      deepLinksController.publishDeepLinkEvent(
        accountID = accountID,
        screenID = screenId,
        barcode = barcode
      )
    }
  }

  private val requestPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { isGranted: Boolean ->
    if (isGranted) {
      // FCM SDK (and your app) can post notifications.
      Toast.makeText(this, getString(R.string.bootPermissionsGiven),Toast.LENGTH_SHORT)
        .show();
    } else {
      // Inform user of the notifications are off and where to turn them on
      Toast.makeText(this, getString(R.string.bootPermissionsNotGiven),
        Toast.LENGTH_LONG).show();
    }
  }

  private fun askNotificationPermission() {
    // This is only necessary for API level >= 33 (TIRAMISU)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
      ) {
        // FCM SDK (and your app) can post notifications.
      } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
        //display an educational UI explaining to the user the features that will be enabled
        //by them granting the POST_NOTIFICATION permission. This UI should provide the user
        //"OK" and "No thanks" buttons. If the user selects "OK," directly request the permission.
        //If the user selects "No thanks," allow the user to continue without notifications.
        MaterialAlertDialogBuilder(this)
          .setTitle(R.string.bootPermissionsTitle)
          .setMessage(R.string.bootPermissionsMessage)
          .setNeutralButton(R.string.bootPermissionsNeutralButton) {_,_ ->
            //do nothing, permissions are not granted
          }
          .setPositiveButton(R.string.bootPermissionsPositiveButton) {_,_ ->
            //Show the permission request
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          }
          .create()
          .show()
      } else {
        // Directly ask for the permission
        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  override val defaultViewModelProviderFactory: ViewModelProvider.Factory
    get() = this.defaultViewModelFactory

  private var mAppCompatDelegate: AppCompatDelegate? = null
  override fun getDelegate(): AppCompatDelegate {
    if (mAppCompatDelegate == null) {
      mAppCompatDelegate = TxNative.wrapAppCompatDelegate(super.getDelegate(), this)
    }

    return mAppCompatDelegate!!
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean(STATE_ACTION_BAR_IS_SHOWING, this.supportActionBar?.isShowing ?: false)
  }

  override fun getActionBar(): ActionBar? {
    this.logger.warn("Use 'getSupportActionBar' instead")
    return super.getActionBar()
  }

  override fun onNewIntent(intent: Intent) {
    if (Services.isInitialized()) {
      if (this.tryToCompleteOAuthIntent(intent)) {
        return
      }
    }
    super.onNewIntent(intent)
  }

  private fun tryToCompleteOAuthIntent(
    intent: Intent
  ): Boolean {
    this.logger.debug("attempting to parse incoming intent as OAuth token")

    val buildConfiguration =
      Services.serviceDirectory()
        .requireService(BuildConfigurationServiceType::class.java)

    val result = OAuthCallbackIntentParsing.processIntent(
      intent = intent,
      requiredScheme = buildConfiguration.oauthCallbackScheme.scheme,
      parseUri = Uri::parse
    )

    if (result is OAuthParseResult.Failed) {
      this.logger.warn("failed to parse incoming intent: {}", result.message)
      return false
    }

    this.logger.debug("parsed OAuth token")
    val accountId = AccountID((result as OAuthParseResult.Success).accountId)
    val token = result.token

    val profilesController =
      Services.serviceDirectory()
        .requireService(ProfilesControllerType::class.java)

    profilesController.profileAccountLogin(
      OAuthWithIntermediaryComplete(
        accountId = accountId,
        token = token
      )
    )
    return true
  }

  override fun onStart() {
    super.onStart()
    this.listenerRepo.registerHandler(this::handleEvent)
    interceptDeepLink()
  }

  override fun onStop() {
    super.onStop()
    this.listenerRepo.unregisterHandler()
  }
  
  override fun onRestart() {
    super.onRestart()
    //When MainActivity is restarted, ask catalog to reload the holds, loans and favorites
    refreshViewModel.setRefreshMessage("reload")
  }

  @Suppress("UNUSED_PARAMETER")
  private fun handleEvent(event: MainActivityListenedEvent, state: Unit) {
    return when (event) {
      is MainActivityListenedEvent.SplashEvent ->
        this.handleSplashEvent(event.event)

      is MainActivityListenedEvent.TutorialEvent ->
        this.handleTutorialEvent(event.event)

      is MainActivityListenedEvent.LoginEvent ->
        this.openMainFragment()

      is MainActivityListenedEvent.OnboardingEvent ->
        this.handleOnboardingEvent(event.event)

      is MainActivityListenedEvent.TextSizeEvent ->
        this.setFontSize(event.event)

      is MainActivityListenedEvent.TipsEvent -> {
        this.handleTipsEvent(event.event)
      }
    }
  }

  private fun handleSplashEvent(event: SplashEvent) {
    return when (event) {
      SplashEvent.SplashCompleted ->
        this.onSplashFinished()
    }
  }

  private fun onSplashFinished() {
    this.logger.debug("onSplashFinished")

    val appCache = AppCache(this)

    if (appCache.isTutorialSeen()) {
      this.onTutorialFinished()
    }
    else {
      this.openTutorial()
      appCache.setTutorialSeen(true)
    }
  }

  private fun handleTutorialEvent(event: TutorialEvent) {
    return when (event) {
      TutorialEvent.TutorialCompleted ->
        this.onTutorialFinished()
    }
  }

  private fun handleTipsEvent(event: TipsEvent) {
    return when (event) {
      TipsEvent.DismissTips ->
        this.dismissTips()
    }
  }

  private fun dismissTips() {
    logger.debug("Dissmiss tips")
    val appCache = AppCache(this)
    appCache.setTipsDismissed(true)
  }

  private fun onTutorialFinished() {
    this.logger.debug("onTutorialFinished")

    val services =
      Services.serviceDirectory()
    val profilesController =
      services.requireService(ProfilesControllerType::class.java)
    val accountProviders =
      services.requireService(AccountProviderRegistryType::class.java)
    val splashService = getSplashService()

    when (profilesController.profileAnonymousEnabled()) {
      ANONYMOUS_PROFILE_ENABLED -> {
        val profile = profilesController.profileCurrent()
        val defaultProvider = accountProviders.defaultProvider

        val hasNonDefaultAccount =
          profile.accounts().values.count { it.provider.id != defaultProvider.id } > 0
        this.logger.debug("hasNonDefaultAccount=$hasNonDefaultAccount")

        val shouldShowLibrarySelectionScreen =
          splashService.shouldShowLibrarySelectionScreen && !profile.preferences().hasSeenLibrarySelectionScreen
        this.logger.debug("shouldShowLibrarySelectionScreen=$shouldShowLibrarySelectionScreen")

        if (!hasNonDefaultAccount && shouldShowLibrarySelectionScreen) {
          this.openOnboarding()
        } else {
          this.onOnboardingFinished()
        }
      }

      ANONYMOUS_PROFILE_DISABLED -> {
        // Not used anymore.
      }
    }
  }

  private fun handleOnboardingEvent(event: OnboardingEvent) {
    return when (event) {
      OnboardingEvent.OnboardingCompleted ->
        this.onOnboardingFinished()
    }
  }

  private fun onOnboardingFinished() {
    this.logger.debug("onBoardingFinished - should OPEN LOGIN")
//    this.openMainFragment()
    this.openLogin()
  }

  private fun openMainBackStack() {
    this.logger.debug("openMain")
    val mainFragment = MainFragment()
    this.supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    this.supportFragmentManager.beginTransaction()
      .replace(R.id.mainFragmentHolder, mainFragment, "MAIN")
      .addToBackStack(null)
      .commit()
  }

  private fun openTestLogin(prefilledUsername: String = "") {
    this.logger.error("openTestLogin($prefilledUsername)")
    val testLoginFragment = TestLoginFragment(prefilledUsername = prefilledUsername)
    this.supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    this.supportFragmentManager.beginTransaction()
      .replace(R.id.mainFragmentHolder, testLoginFragment, "TEST_LOGIN")
      .addToBackStack(null)
      .commit()
  }

  private fun getSplashService(): BrandingSplashServiceType {
    return ServiceLoader
      .load(BrandingSplashServiceType::class.java)
      .firstOrNull()
      ?: throw IllegalStateException(
        "No available services of type ${BrandingSplashServiceType::class.java.canonicalName}"
      )
  }

  private fun openSplashScreen() {
    this.logger.debug("openSplashScreen")
    val splashFragment = SplashFragment()
    this.supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    this.supportFragmentManager.commit {
      replace(R.id.mainFragmentHolder, splashFragment, "SPLASH_MAIN")
    }
  }

  private fun openTutorial() {
    this.logger.debug("openTutorial")
    val tutorialFragment = TutorialFragment()
    this.supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    this.supportFragmentManager.commit {
      replace(R.id.mainFragmentHolder, tutorialFragment)
    }
  }

  private fun openLogin() {
    this.logger.debug("openLogin")
    replaceFragment(LoginMainFragment())
  }

  private fun openOnboarding() {
    this.logger.debug("openOnboarding")
    replaceFragment(OnboardingFragment())

  }

  private fun openMainFragment() {
    this.logger.debug("openMainFragment")
    replaceFragment(MainFragment(), "MAIN")
  }

  private fun replaceFragment(fragment: Fragment, tag: String = "") {
    this.supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    this.supportFragmentManager.commit {
      replace(R.id.mainFragmentHolder, fragment, tag)
    }
  }
}
