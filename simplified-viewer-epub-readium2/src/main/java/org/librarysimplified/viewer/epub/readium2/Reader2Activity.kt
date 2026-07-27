package org.librarysimplified.viewer.epub.readium2

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.transifex.txnative.TxNative
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.runBlocking
import org.joda.time.LocalDateTime
import org.librarysimplified.mdc.MDCKeys
import org.librarysimplified.r2.api.SR2Bookmark
import org.librarysimplified.r2.api.SR2Command
import org.librarysimplified.r2.api.SR2ControllerType
import org.librarysimplified.r2.api.SR2Event
import org.librarysimplified.r2.api.SR2Event.SR2BookmarkEvent.SR2BookmarkCreated
import org.librarysimplified.r2.api.SR2Event.SR2BookmarkEvent.SR2BookmarkDeleted
import org.librarysimplified.r2.api.SR2PageNumberingMode
import org.librarysimplified.r2.api.SR2ScrollingMode.SCROLLING_MODE_CONTINUOUS
import org.librarysimplified.r2.api.SR2ScrollingMode.SCROLLING_MODE_PAGINATED
import org.librarysimplified.r2.vanilla.SR2Controllers
import org.librarysimplified.r2.views.SR2Fragment
import org.librarysimplified.r2.views.SR2ReaderFragment
import org.librarysimplified.r2.views.SR2ReaderModel
import org.librarysimplified.r2.views.SR2ReaderViewCommand
import org.librarysimplified.r2.views.SR2ReaderViewCommand.SR2ReaderViewNavigationReaderClose
import org.librarysimplified.r2.views.SR2ReaderViewCommand.SR2ReaderViewNavigationSearchClose
import org.librarysimplified.r2.views.SR2ReaderViewCommand.SR2ReaderViewNavigationSearchOpen
import org.librarysimplified.r2.views.SR2ReaderViewCommand.SR2ReaderViewNavigationTOCClose
import org.librarysimplified.r2.views.SR2ReaderViewCommand.SR2ReaderViewNavigationTOCOpen
import org.librarysimplified.r2.views.SR2ReaderViewEvent
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewBookEvent.SR2BookLoadingFailed
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewControllerEvent.SR2ControllerBecameAvailable
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewControllerEvent.SR2ControllerBecameUnavailable
import org.librarysimplified.r2.views.SR2SearchFragment
import org.librarysimplified.r2.views.SR2TOCFragment
import org.librarysimplified.services.api.Services
import org.nypl.drm.core.AdobeAdeptAssets
import org.nypl.drm.core.AdobeAdeptLoan
import org.nypl.drm.core.ContentProtectionProvider
import org.nypl.simplified.accessibility.AccessibilityServiceType
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.analytics.api.AnalyticsEvent
import org.nypl.simplified.analytics.api.AnalyticsType
import org.nypl.simplified.bookmarks.api.BookmarkServiceType
import org.nypl.simplified.books.api.BookContentProtections
import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.thread.api.UIThreadServiceType
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.Asset
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.asset.ContainerAsset
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.io.File
import java.io.IOException
import java.util.ServiceLoader
import java.util.concurrent.ExecutionException

/**
 * The main reader activity for reading an EPUB using Readium 2 (SR2 6.x).
 */

class Reader2Activity : AppCompatActivity(R.layout.reader2) {

  companion object {

    private const val ARG_PARAMETERS =
      "org.nypl.simplified.viewer.epub.readium2.ReaderActivity2.parameters"

    /**
     * Start a new reader for the given book.
     */

    fun startActivity(
      context: Activity,
      parameters: Reader2ActivityParameters
    ) {
      val intent = Intent(context, Reader2Activity::class.java)
      val bundle = Bundle().apply {
        this.putSerializable(ARG_PARAMETERS, parameters)
      }
      intent.putExtras(bundle)
      context.startActivity(intent)
    }
  }

  private val logger =
    LoggerFactory.getLogger(Reader2Activity::class.java)

  private val services =
    Services.serviceDirectory()
  private val accessibilityService =
    services.requireService(AccessibilityServiceType::class.java)
  private val analyticsService =
    services.requireService(AnalyticsType::class.java)
  private val bookmarkService =
    services.requireService(BookmarkServiceType::class.java)
  private val profilesController =
    services.requireService(ProfilesControllerType::class.java)
  private val uiThread =
    services.requireService(UIThreadServiceType::class.java)
  private val contentProtectionProviders =
    ServiceLoader.load(ContentProtectionProvider::class.java).toList()

  private lateinit var account: AccountType
  private lateinit var parameters: Reader2ActivityParameters
  private lateinit var fragmentHostView: View
  private var fragmentNow: Fragment? = null
  private var subscriptions: CompositeDisposable = CompositeDisposable()

  /**
   * If a cross-device "last read" divergence is detected at reader startup, the prompt details
   * are recorded here and the dialog is shown once the controller becomes available. The
   * controller is started having opened the *local* position, so the prompt offers to jump to
   * the (newer) server position.
   */

  private var pendingBookmarkPrompt: BookmarkPrompt? = null

  private data class BookmarkPrompt(
    val local: SR2Bookmark,
    val server: SR2Bookmark
  )

  private var mAppCompatDelegate: AppCompatDelegate? = null
  override fun getDelegate(): AppCompatDelegate {
    if (mAppCompatDelegate == null) {
      mAppCompatDelegate = TxNative.wrapAppCompatDelegate(super.getDelegate(), this)
    }

    return mAppCompatDelegate!!
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    this.enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    this.logger.debug(
      "loaded {} content protection providers",
      this.contentProtectionProviders.size
    )
    this.contentProtectionProviders.forEachIndexed { index, provider ->
      this.logger.debug("[{}] available provider {}", index, provider.javaClass.canonicalName)
    }

    val intent =
      this.intent ?: throw IllegalStateException("ReaderActivity2 requires an intent")
    val extras =
      intent.extras ?: throw IllegalStateException("ReaderActivity2 Intent lacks parameters")

    this.parameters =
      extras.getSerializable(ARG_PARAMETERS) as Reader2ActivityParameters

    MDC.put(MDCKeys.ACCOUNT_INTERNAL_ID, this.parameters.accountId.uuid.toString())
    MDC.put(MDCKeys.BOOK_INTERNAL_ID, this.parameters.bookId.value())
    MDC.put(MDCKeys.BOOK_TITLE, this.parameters.entry.feedEntry.title)
    MDCKeys.put(MDCKeys.BOOK_PUBLISHER, this.parameters.entry.feedEntry.publisher)
    MDC.put(MDCKeys.BOOK_DRM, this.parameters.drmInfo.kind.name)
    MDC.remove(MDCKeys.BOOK_FORMAT)

    try {
      this.account =
        this.profilesController.profileCurrent()
          .account(this.parameters.accountId)
      MDC.put(MDCKeys.ACCOUNT_PROVIDER_ID, account.provider.id.toString())
    } catch (e: Exception) {
      this.logger.error("unable to locate account: ", e)
      this.finish()
      return
    }

    // Find the fragment host view
    this.fragmentHostView = findViewById(R.id.reader2FragmentHost)

    // Apply window insets to the fragment host view
    ViewCompat.setOnApplyWindowInsetsListener(this.fragmentHostView) { view, insets ->
      val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        topMargin = bars.top
        leftMargin = bars.left
        rightMargin = bars.right
        bottomMargin = bars.bottom
      }
      WindowInsetsCompat.CONSUMED
    }

    /*
     * Enable webview debugging for debug builds
     */

    if ((this.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
      WebView.setWebContentsDebuggingEnabled(true)
    }
  }

  override fun onStart() {
    super.onStart()

    this.subscriptions = CompositeDisposable()
    this.subscriptions.add(SR2ReaderModel.controllerEvents.subscribe(this::onControllerEvent))
    this.subscriptions.add(SR2ReaderModel.viewCommands.subscribe(this::onViewCommandReceived))
    this.subscriptions.add(SR2ReaderModel.viewEvents.subscribe(this::onViewEventReceived))

    this.switchFragment(Reader2LoadingFragment())
    this.startReader()
  }

  override fun onStop() {
    super.onStop()

    val fragment = this.fragmentNow
    if (fragment != null) {
      this.supportFragmentManager.beginTransaction()
        .remove(fragment)
        .commitAllowingStateLoss()
    }

    this.subscriptions.dispose()

    /*
     * If the activity is finishing, send an analytics event.
     */

    if (this.isFinishing && this::account.isInitialized) {
      val profile = this.profilesController.profileCurrent()

      this.analyticsService.publishEvent(
        AnalyticsEvent.BookClosed(
          timestamp = LocalDateTime.now(),
          credentials = this.account.loginState.credentials,
          profileUUID = profile.id.uuid,
          accountProvider = this.account.provider.id,
          accountUUID = this.account.id.uuid,
          opdsEntry = this.parameters.entry.feedEntry
        )
      )
    }
  }

  /**
   * Start the reader. In SR2 6.x the controller is created asynchronously via the global
   * [SR2ReaderModel]; the bookmarks (including the last-read location) are handed to the
   * controller, which opens the most recent reading position itself.
   */

  private fun startReader() {
    this.uiThread.checkIsUIThread()

    try {
      val profileCurrent =
        this.profilesController.profileCurrent()

      /*
       * Load any bookmarks.
       */

      val bookmarks =
        Reader2Bookmarks.loadBookmarks(
          bookmarkService = this.bookmarkService,
          accountID = this.parameters.accountId,
          bookID = this.parameters.bookId
        )

      /*
       * Detect a cross-device "last read" divergence (a local and a server last-read location
       * that point at different places, with the server one being newer). If found, withhold the
       * server bookmark so the controller opens the local position, and remember to prompt the
       * user once the controller is available. This preserves the e-kirjasto reading-position
       * prompt under the new SR2 architecture, which otherwise opens a last-read location without
       * asking.
       */

      this.pendingBookmarkPrompt = this.computeBookmarkDivergence(bookmarks)
      val initialBookmarks =
        when (val prompt = this.pendingBookmarkPrompt) {
          null -> bookmarks
          else -> bookmarks.filterNot { it === prompt.server }
        }

      /*
       * Load the most recently configured theme from the profile's preferences.
       */

      val initialTheme =
        Reader2Themes.toSR2(profileCurrent.preferences().readerPreferences)

      /*
       * Instantiate any content protections that might be needed for DRM...
       */

      val contentProtections =
        BookContentProtections.create(
          context = this,
          contentProtectionProviders = this.contentProtectionProviders,
          drmInfo = this.parameters.drmInfo,
          isManualPassphraseEnabled =
          profileCurrent.preferences().isManualLCPPassphraseEnabled,
          onLCPDialogDismissed = {
            this.logger.debug("Dismissed LCP dialog. Shutting down...")
            this.finish()
          }
        )

      /*
       * Open the asset. The asset retriever auto-detects whether the file is LCP-wrapped or a
       * plain EPUB.
       */

      this.logger.debug("Opening asset...")
      val assetRetriever =
        AssetRetriever(
          contentResolver = this.contentResolver,
          httpClient = DefaultHttpClient()
        )

      val rawBookAsset =
        when (val a = runBlocking { assetRetriever.retrieve(this@Reader2Activity.parameters.file) }) {
          is Try.Failure -> throw IOException(a.value.message)
          is Try.Success -> a.value
        }

      this.logger.debug("DRM info: {}", this.parameters.drmInfo)
      val bookAsset =
        when (val drmInfo = this.parameters.drmInfo) {
          is BookDRMInformation.ACS ->
            this.openWithAdobe(rawBookAsset, drmInfo.rights)

          is BookDRMInformation.LCP ->
            rawBookAsset

          BookDRMInformation.None ->
            rawBookAsset
        }

      /*
       * Configure the global reader model and create the controller.
       */

      SR2ReaderModel.scrollMode =
        if (this.accessibilityService.spokenFeedbackEnabled) {
          SCROLLING_MODE_CONTINUOUS
        } else {
          SCROLLING_MODE_PAGINATED
        }
      SR2ReaderModel.perChapterNumbering =
        SR2PageNumberingMode.WHOLE_BOOK

      SR2ReaderModel.controllerCreate(
        context = this.application,
        contentProtections = contentProtections,
        bookFile = bookAsset,
        bookId = this.parameters.entry.feedEntry.id,
        theme = initialTheme,
        controllers = SR2Controllers(),
        bookmarks = initialBookmarks,
        allowCopyPaste = this.parameters.drmInfo is BookDRMInformation.None
      )
    } catch (e: Exception) {
      this.onBookLoadingFailed(e)
    }
  }

  /**
   * Open an Adobe ACS-encrypted EPUB. e-kirjasto only ships LCP today, but Adobe support is wired
   * up (see MainServices) and so the branch is preserved.
   */

  @Throws(IOException::class)
  private fun openWithAdobe(
    rawBookAsset: Asset,
    rights: Pair<File, AdobeAdeptLoan>?
  ): Asset {
    if (rawBookAsset !is ContainerAsset) {
      throw IOException("Attempted to open something that is not an EPUB file.")
    }
    if (rights == null) {
      throw IOException("Missing Adobe rights information.")
    }
    return AdobeAdeptAssets.openAsset(
      epubAsset = rawBookAsset,
      rightsFile = rights.first
    )
  }

  /**
   * Determine whether the local and server last-read locations diverge such that the user should
   * be prompted. Mirrors the behaviour of the original (SR2 2.x) implementation: the bookmark
   * list is ordered [local, server, ...explicit], so the first last-read entry is the local one
   * and the last is the server one.
   */

  private fun computeBookmarkDivergence(
    bookmarks: List<SR2Bookmark>
  ): BookmarkPrompt? {
    val lastRead = bookmarks.filter { it.type == SR2Bookmark.Type.LAST_READ }
    if (lastRead.size > 1) {
      val local = lastRead.first()
      val server = lastRead.last()
      if (server.date.isAfter(local.date) && local.locator != server.locator) {
        return BookmarkPrompt(local = local, server = server)
      }
    }
    return null
  }

  private fun switchFragment(fragment: Fragment) {
    this.fragmentNow = fragment
    this.supportFragmentManager.beginTransaction()
      .replace(R.id.reader2FragmentHost, fragment)
      .commitAllowingStateLoss()
  }

  /**
   * Handle incoming view events (book loaded/failed, controller availability).
   */

  private fun onViewEventReceived(event: SR2ReaderViewEvent) {
    this.uiThread.checkIsUIThread()

    return when (event) {
      is SR2BookLoadingFailed ->
        this.onBookLoadingFailed(event.exception)

      is SR2ControllerBecameAvailable ->
        this.onControllerBecameAvailable(event.controller)

      is SR2ControllerBecameUnavailable -> {
        // Nothing to do
      }
    }
  }

  /**
   * Handle incoming view commands (navigation between reader/TOC/search).
   */

  private fun onViewCommandReceived(command: SR2ReaderViewCommand) {
    this.uiThread.checkIsUIThread()

    return when (command) {
      SR2ReaderViewNavigationReaderClose ->
        this.finish()

      SR2ReaderViewNavigationSearchClose ->
        this.switchFragment(SR2ReaderFragment())

      SR2ReaderViewNavigationSearchOpen ->
        this.switchFragment(SR2SearchFragment())

      SR2ReaderViewNavigationTOCClose ->
        this.switchFragment(SR2ReaderFragment())

      SR2ReaderViewNavigationTOCOpen ->
        this.switchFragment(SR2TOCFragment())
    }
  }

  private fun onControllerBecameAvailable(
    controller: SR2ControllerType
  ) {
    this.uiThread.checkIsUIThread()
    this.switchFragment(SR2ReaderFragment())

    /*
     * If a divergent server last-read location was found at startup, prompt the user now. The
     * controller has already opened the local position.
     */

    val prompt = this.pendingBookmarkPrompt
    if (prompt != null) {
      this.pendingBookmarkPrompt = null
      this.showBookmarkPrompt(controller, prompt.local, prompt.server)
    }
  }

  /**
   * Handle incoming events from the controller.
   */

  private fun onControllerEvent(event: SR2Event) {
    when (event) {
      is SR2Event.SR2ThemeChanged -> {
        this.profilesController.profileUpdate { current ->
          current.copy(
            preferences = current.preferences.copy(
              readerPreferences = Reader2Themes.fromSR2(event.theme)
            )
          )
        }
        Unit
      }

      is SR2BookmarkCreated -> {
        val localBookmark =
          Reader2Bookmarks.fromSR2Bookmark(
            bookEntry = this.parameters.entry,
            deviceId = Reader2Devices.deviceId(this.profilesController, this.parameters.bookId),
            source = event.bookmark
          )

        when (localBookmark.kind) {
          BookmarkKind.BookmarkExplicit -> this.showToastMessage(R.string.reader_bookmark_added)
          BookmarkKind.BookmarkLastReadLocation -> Unit
        }

        this.bookmarkService.bookmarkCreate(
          accountID = this.parameters.accountId,
          bookmark = localBookmark,
          ignoreRemoteFailures = true
        )
        Unit
      }

      is SR2BookmarkDeleted -> {
        val localBookmark =
          Reader2Bookmarks.fromSR2Bookmark(
            bookEntry = this.parameters.entry,
            deviceId = Reader2Devices.deviceId(this.profilesController, this.parameters.bookId),
            source = event.bookmark
          )

        this.bookmarkService.bookmarkDelete(
          accountID = this.account.id,
          bookmark = localBookmark,
          ignoreRemoteFailures = true
        )
        Unit
      }

      is SR2Event.SR2CommandEvent,
      is SR2Event.SR2Error.SR2ChapterNonexistent,
      is SR2Event.SR2Error.SR2WebViewInaccessible,
      is SR2Event.SR2ExternalLinkSelected,
      is SR2Event.SR2OnCenterTapped,
      is SR2Event.SR2ReadingPositionChanged -> {
        // Nothing
      }
    }
  }

  /**
   * Show the reading-position prompt when the local and server last-read locations diverge. The
   * controller has opened the local position; the user may choose to jump to the server position
   * instead.
   */

  private fun showBookmarkPrompt(
    controller: SR2ControllerType,
    localLastReadBookmark: SR2Bookmark,
    serverLastReadBookmark: SR2Bookmark
  ) {
    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.reader_position_title)
      .setMessage(R.string.reader_position_message)
      .setNegativeButton(R.string.reader_position_move) { dialog, _ ->
        dialog.dismiss()
        this.createLocalBookmarkFromPromptAction(bookmark = serverLastReadBookmark)
        controller.submitCommand(SR2Command.OpenChapter(serverLastReadBookmark.locator))
      }
      .setPositiveButton(R.string.reader_position_stay) { dialog, _ ->
        dialog.dismiss()
        this.createLocalBookmarkFromPromptAction(bookmark = localLastReadBookmark)
        controller.submitCommand(SR2Command.OpenChapter(localLastReadBookmark.locator))
      }
      .create()
      .show()
  }

  private fun createLocalBookmarkFromPromptAction(bookmark: SR2Bookmark) {
    // we need to create a local bookmark after choosing an option from the prompt because the local
    // bookmark is no longer created when syncing from the server returns a last read location
    // bookmark
    this.bookmarkService.bookmarkCreateLocal(
      accountID = this.parameters.accountId,
      bookmark = Reader2Bookmarks.fromSR2Bookmark(
        bookEntry = this.parameters.entry,
        deviceId = Reader2Devices.deviceId(
          this.profilesController,
          this.parameters.bookId
        ),
        source = bookmark
      )
    )
  }

  @Deprecated("Deprecated in Java")
  override fun onBackPressed() {
    return when (val f = this.fragmentNow) {
      is SR2Fragment -> {
        when (f) {
          is SR2ReaderFragment ->
            super.onBackPressed()

          is SR2SearchFragment ->
            this.switchFragment(SR2ReaderFragment())

          is SR2TOCFragment ->
            this.switchFragment(SR2ReaderFragment())
        }
      }

      else ->
        super.onBackPressed()
    }
  }

  /**
   * Loading a book failed.
   */

  private fun onBookLoadingFailed(
    exception: Throwable
  ) {
    this.uiThread.checkIsUIThread()

    val actualException =
      if (exception is ExecutionException) {
        exception.cause ?: exception
      } else {
        exception
      }

    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.bookOpenFailedTitle)
      .setMessage(
        this.getString(
          R.string.bookOpenFailedMessage,
          actualException.javaClass.name,
          actualException.message
        )
      )
      .setOnDismissListener { this.finish() }
      .create()
      .show()
  }

  private fun showToastMessage(@StringRes messageRes: Int) {
    this.runOnUiThread {
      Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }
  }
}
