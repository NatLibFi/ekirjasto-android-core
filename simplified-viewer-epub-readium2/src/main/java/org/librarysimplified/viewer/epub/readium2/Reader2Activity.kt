package org.librarysimplified.viewer.epub.readium2

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.webkit.WebView
import android.widget.Toast
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.transifex.txnative.TxNative
import io.reactivex.disposables.Disposable
import org.joda.time.LocalDateTime
import org.librarysimplified.mdc.MDCKeys
import org.librarysimplified.r2.api.SR2Bookmark
import org.librarysimplified.r2.api.SR2Command
import org.librarysimplified.r2.api.SR2ControllerType
import org.librarysimplified.r2.api.SR2Event
import org.librarysimplified.r2.api.SR2Event.SR2CommandEvent.SR2CommandEventCompleted.SR2CommandExecutionFailed
import org.librarysimplified.r2.api.SR2Event.SR2CommandEvent.SR2CommandEventCompleted.SR2CommandExecutionSucceeded
import org.librarysimplified.r2.api.SR2Event.SR2CommandEvent.SR2CommandExecutionRunningLong
import org.librarysimplified.r2.api.SR2Event.SR2CommandEvent.SR2CommandExecutionStarted
import org.librarysimplified.r2.api.SR2Event.SR2CommandEvent.SR2CommandSearchResults
import org.librarysimplified.r2.api.SR2Event.SR2Error.SR2ChapterNonexistent
import org.librarysimplified.r2.api.SR2Event.SR2Error.SR2WebViewInaccessible
import org.librarysimplified.r2.api.SR2Event.SR2ExternalLinkSelected
import org.librarysimplified.r2.api.SR2PageNumberingMode
import org.librarysimplified.r2.api.SR2ScrollingMode.SCROLLING_MODE_CONTINUOUS
import org.librarysimplified.r2.api.SR2ScrollingMode.SCROLLING_MODE_PAGINATED
import org.librarysimplified.r2.vanilla.SR2Controllers
import org.librarysimplified.r2.views.SR2ControllerReference
import org.librarysimplified.r2.views.SR2ReaderFragment
import org.librarysimplified.r2.views.SR2ReaderFragmentFactory
import org.librarysimplified.r2.views.SR2ReaderParameters
import org.librarysimplified.r2.views.SR2ReaderViewEvent
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewBookEvent.SR2BookLoadingFailed
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewControllerEvent.SR2ControllerBecameAvailable
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewNavigationEvent.SR2ReaderViewNavigationClose
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewNavigationEvent.SR2ReaderViewNavigationOpenSearch
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewNavigationEvent.SR2ReaderViewNavigationOpenTOC
import org.librarysimplified.r2.views.SR2ReaderViewModel
import org.librarysimplified.r2.views.SR2ReaderViewModelFactory
import org.librarysimplified.r2.views.SR2TOCFragment
import org.librarysimplified.r2.views.search.SR2SearchFragment
import org.librarysimplified.services.api.Services
import org.nypl.drm.core.AdobeAdeptFileAsset
import org.nypl.drm.core.AxisNowFileAsset
import org.nypl.drm.core.ContentProtectionProvider
import org.nypl.simplified.accessibility.AccessibilityServiceType
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.analytics.api.AnalyticsEvent
import org.nypl.simplified.analytics.api.AnalyticsType
import org.nypl.simplified.bookmarks.api.BookmarkServiceType
import org.nypl.simplified.books.api.BookContentProtections
import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.futures.FluentFutureExtensions.map
import org.nypl.simplified.futures.FluentFutureExtensions.onAnyError
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.thread.api.UIThreadServiceType
import org.readium.r2.shared.publication.asset.FileAsset
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.ServiceLoader
import java.util.concurrent.ExecutionException

/**
 * The main reader activity for reading an EPUB using Readium 2.
 */

class Reader2Activity : AppCompatActivity(R.layout.reader2) {

  companion object {

    private const val ARG_PARAMETERS =
      "org.nypl.simplified.viewer.epub.readium2.ReaderActivity2.parameters"

    private const val READER_FRAGMENT_TAG =
      "org.librarysimplified.r2.views.SR2ReaderFragment"

    private const val TOC_FRAGMENT_TAG =
      "org.librarysimplified.r2.views.SR2TOCFragment"

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
  private lateinit var readerFragment: Fragment
  private lateinit var readerModel: SR2ReaderViewModel
  private lateinit var searchFragment: Fragment
  private lateinit var tocFragment: Fragment
  private var controller: SR2ControllerType? = null
  private var controllerSubscription: Disposable? = null
  private var viewSubscription: Disposable? = null

  private var mAppCompatDelegate: AppCompatDelegate? = null
  override fun getDelegate(): AppCompatDelegate {
    if (mAppCompatDelegate == null) {
      mAppCompatDelegate = TxNative.wrapAppCompatDelegate(super.getDelegate(), this)
    }

    return mAppCompatDelegate!!
  }

  override fun onCreate(savedInstanceState: Bundle?) {
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

    val readerParameters =
      this.computeReaderParameters()

    this.supportFragmentManager.fragmentFactory =
      SR2ReaderFragmentFactory(readerParameters)

    super.onCreate(savedInstanceState)

    this.readerModel =
      ViewModelProvider(this, SR2ReaderViewModelFactory(readerParameters))
        .get(SR2ReaderViewModel::class.java)

    /*
     * Enable webview debugging for debug builds
     */

    if ((this.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
      WebView.setWebContentsDebuggingEnabled(true)
    }

    //Check if there are fragments with matching tags already in the backstack
    //If yes, set the existing ones into the variables
    //If no, initiate a new fragment of the same kind
      this.readerFragment = this.supportFragmentManager.findFragmentByTag("reader") ?:
      this.supportFragmentManager.fragmentFactory.instantiate(
      this.classLoader,
      SR2ReaderFragment::class.java.name
      )

      this.searchFragment = this.supportFragmentManager.findFragmentByTag("search") ?:
        this.supportFragmentManager.fragmentFactory.instantiate(
          this.classLoader,
          SR2SearchFragment::class.java.name
        )

      this.tocFragment = this.supportFragmentManager.findFragmentByTag("toc") ?:
        this.supportFragmentManager.fragmentFactory.instantiate(
          this.classLoader,
          SR2TOCFragment::class.java.name
        )

    if (savedInstanceState == null) {
      //If this is the first time using onCreate
      //Add the reader to the stack, add a tag to find the fragment on reconfiguration
        this.supportFragmentManager.beginTransaction()
          .add(R.id.reader2FragmentHost, this.readerFragment, "reader")
          .commit()
    }
  }

  override fun onStart() {
    super.onStart()

    this.viewSubscription =
      this.readerModel.viewEvents.subscribe(this::onViewEvent)
  }

  override fun onStop() {
    super.onStop()
    this.controllerSubscription?.dispose()
    this.viewSubscription?.dispose()
    /*
     * If the activity is finishing, send an analytics event.
     */

    if (this.isFinishing) {
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

  private fun computeReaderParameters(): SR2ReaderParameters {
    /*
     * Instantiate any content protections that might be needed for DRM...
     */

    val contentProtections = BookContentProtections.create(
      context = this,
      contentProtectionProviders = this.contentProtectionProviders,
      drmInfo = this.parameters.drmInfo,
      isManualPassphraseEnabled =
      profilesController.profileCurrent().preferences().isManualLCPPassphraseEnabled,
      onLCPDialogDismissed = {
        finish()
      }
    )

    /*
     * Load the most recently configured theme from the profile's preferences.
     */

    val initialTheme =
      Reader2Themes.toSR2(
        this.profilesController.profileCurrent()
          .preferences()
          .readerPreferences
      )

    val bookFile =
      when (val drmInfo = this.parameters.drmInfo) {
        is BookDRMInformation.ACS ->
          AdobeAdeptFileAsset(
            fileAsset = FileAsset(this.parameters.file),
            adobeRightsFile = drmInfo.rights?.first
          )

        is BookDRMInformation.AXIS ->
          AxisNowFileAsset(
            fileAsset = FileAsset(this.parameters.file),
            axisLicense = drmInfo.license,
            axisUserKey = drmInfo.userKey
          )

        else -> FileAsset(this.parameters.file)
      }

    return SR2ReaderParameters(
      contentProtections = contentProtections,
      bookFile = bookFile,
      bookId = this.parameters.entry.feedEntry.id,
      isPreview = false,
      theme = initialTheme,
      controllers = SR2Controllers(),
      scrollingMode = if (this.accessibilityService.spokenFeedbackEnabled) {
        SCROLLING_MODE_CONTINUOUS
      } else {
        SCROLLING_MODE_PAGINATED
      },
      pageNumberingMode = SR2PageNumberingMode.WHOLE_BOOK
    )
  }

  /**
   * Handle incoming messages from the view fragments.
   */

  private fun onViewEvent(event: SR2ReaderViewEvent) {
    this.uiThread.checkIsUIThread()

    return when (event) {
      SR2ReaderViewNavigationClose ->
        this.onBackPressed()

      SR2ReaderViewNavigationOpenTOC ->
        this.tocOpen()

      is SR2ControllerBecameAvailable ->
        this.onControllerBecameAvailable(event.reference)

      is SR2BookLoadingFailed ->
        this.onBookLoadingFailed(event.exception)

      SR2ReaderViewNavigationOpenSearch -> {
        this.openSearch()
      }
    }
  }

  private fun onControllerBecameAvailable(reference: SR2ControllerReference) {
    this.controller = reference.controller

    /*
     * Subscribe to messages from the controller.
     */

    this.controllerSubscription =
      reference.controller.events.subscribe(this::onControllerEvent)

    if (reference.isFirstStartup) {
      val bookmarks =
        Reader2Bookmarks.loadBookmarks(
          bookmarkService = this.bookmarkService,
          accountID = this.parameters.accountId,
          bookID = this.parameters.bookId
        )

      reference.controller.submitCommand(SR2Command.BookmarksLoad(bookmarks))

      val lastReadBookmarks = bookmarks.filter { bookmark ->
        bookmark.type == SR2Bookmark.Type.LAST_READ
      }

      // if there's more than one last read bookmark, we'll need to compare their dates
      if (lastReadBookmarks.size > 1) {
        val localLastReadBookmark = lastReadBookmarks.first()
        val serverLastReadBookmark = lastReadBookmarks.last()

        if (serverLastReadBookmark.date.isAfter(localLastReadBookmark.date) &&
          localLastReadBookmark.locator != serverLastReadBookmark.locator
        ) {
          showBookmarkPrompt(reference.controller, localLastReadBookmark, serverLastReadBookmark)
        } else {
          reference.controller.submitCommand(SR2Command.OpenChapter(localLastReadBookmark.locator))
        }
      } else if (lastReadBookmarks.isNotEmpty()) {
        reference.controller.submitCommand(
          SR2Command.OpenChapter(
            lastReadBookmarks.first().locator
          )
        )
      } else {
        val startLocator = reference.controller.bookMetadata.start
        reference.controller.submitCommand(SR2Command.OpenChapter(startLocator))
      }
    } else {
      // Refresh whatever the controller was looking at previously.
      reference.controller.submitCommand(SR2Command.Refresh)
    }
  }

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
        createLocalBookmarkFromPromptAction(
          bookmark = serverLastReadBookmark
        )
        controller.submitCommand(
          SR2Command.OpenChapter(
            serverLastReadBookmark.locator
          )
        )
      }
      .setPositiveButton(R.string.reader_position_stay) { dialog, _ ->
        dialog.dismiss()
        createLocalBookmarkFromPromptAction(
          bookmark = localLastReadBookmark
        )
        controller.submitCommand(
          SR2Command.OpenChapter(
            localLastReadBookmark.locator
          )
        )
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
    if (this::tocFragment.isInitialized) {
      if (this.tocFragment.isVisible) {
        this.tocClose()
        return
      }
    }

    if (this::searchFragment.isInitialized) {
      if (this.searchFragment.isVisible) {
        this.searchClose()
        return
      }
    }

    super.onBackPressed()
  }

  /**
   * Handle incoming messages from the controller.
   */

  private fun onControllerEvent(
    event: SR2Event
  ) {
    when (event) {
      is SR2Event.SR2BookmarkEvent.SR2BookmarkCreate -> {
        val localBookmark =
          Reader2Bookmarks.fromSR2Bookmark(
            bookEntry = this.parameters.entry,
            deviceId = Reader2Devices.deviceId(this.profilesController, this.parameters.bookId),
            source = event.bookmark
          )

        when (localBookmark.kind) {
          BookmarkKind.BookmarkExplicit -> showToastMessage(R.string.reader_bookmark_adding)
          BookmarkKind.BookmarkLastReadLocation -> Unit
        }

        this.bookmarkService.bookmarkCreate(
          accountID = parameters.accountId,
          bookmark = localBookmark,
          ignoreRemoteFailures = true
        ).map { createdBookmark ->
          event.onBookmarkCreationCompleted(Reader2Bookmarks.toSR2Bookmark(createdBookmark))
          when (createdBookmark.kind) {
            BookmarkKind.BookmarkExplicit -> showToastMessage(R.string.reader_bookmark_added)
            BookmarkKind.BookmarkLastReadLocation -> Unit
          }
        }
      }

      is SR2Event.SR2BookmarkEvent.SR2BookmarkTryToDelete -> {
        val bookmark =
          Reader2Bookmarks.fromSR2Bookmark(
            bookEntry = this.parameters.entry,
            deviceId = Reader2Devices.deviceId(this.profilesController, this.parameters.bookId),
            source = event.bookmark
          )

        bookmarkService.bookmarkDelete(
          accountID = account.id,
          bookmark = bookmark,
          ignoreRemoteFailures = true
        ).map {
          event.onDeleteOperationFinished(true)
        }.onAnyError {
          event.onDeleteOperationFinished(false)
        }
      }

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

      is SR2Event.SR2OnCenterTapped,
      is SR2Event.SR2ReadingPositionChanged,
      SR2Event.SR2BookmarkEvent.SR2BookmarksLoaded,
      SR2Event.SR2BookmarkEvent.SR2BookmarkFailedToBeDeleted,
      is SR2Event.SR2BookmarkEvent.SR2BookmarkDeleted,
      is SR2Event.SR2BookmarkEvent.SR2BookmarkCreated,
      is SR2ChapterNonexistent,
      is SR2WebViewInaccessible,
      is SR2ExternalLinkSelected,
      is SR2CommandExecutionStarted,
      is SR2CommandExecutionRunningLong,
      is SR2CommandExecutionSucceeded,
      is SR2CommandExecutionFailed,
      is SR2CommandSearchResults -> {
        // Nothing
      }
    }
  }

  /**
   * Close the table of contents.
   */

  private fun tocClose() {
    this.uiThread.checkIsUIThread()

    this.logger.debug("TOC closing")
    this.supportFragmentManager.beginTransaction()
      .hide(this.tocFragment)
      .show(this.readerFragment)
      .commit()
  }

  /**
   * Open the table of contents.
   */

  private fun tocOpen() {
    this.uiThread.checkIsUIThread()

    this.logger.debug("TOC opening")

    // Work around a lifecycle related crash.
    if (!this::readerFragment.isInitialized) {
      return
    }

    //Hide the readerFragment to show the toc
    val transaction =
      this.supportFragmentManager.beginTransaction()
        .hide(this.readerFragment)

    //If a toc has already been added, show it, if not create a new one
    if (this.tocFragment.isAdded) {
      transaction.show(tocFragment)
    } else {
      //Add the toc to the stack, add a tag to find the fragment on reconfiguration
      transaction.add(R.id.reader2FragmentHost, this.tocFragment, "toc")
    }

    transaction.commit()
  }

  /**
   * Close the searching screen
   */

  private fun searchClose() {
    this.uiThread.checkIsUIThread()

    this.logger.debug("Search closing")
    this.supportFragmentManager.beginTransaction()
      .hide(this.searchFragment)
      .show(this.readerFragment)
      .commit()
  }

  /**
   * Open the searching screen
   */

  private fun openSearch() {
    this.uiThread.checkIsUIThread()

    this.logger.debug("Search opening")

    //Hide the reader to show search
    val transaction = this.supportFragmentManager.beginTransaction()
      .hide(this.readerFragment)

    //If there already is a searchFragment in the stack, show it, if not, create a new one
    if (this.searchFragment.isAdded) {
      transaction.show(searchFragment)
    } else {
      //Add the search to the stack, add a tag to find the fragment on reconfiguration
      transaction.add(R.id.reader2FragmentHost, this.searchFragment, "search")
    }

    transaction.commit()
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
    runOnUiThread {
      Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }
  }
}
