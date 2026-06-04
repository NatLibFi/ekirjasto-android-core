package org.librarysimplified.viewer.preview

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.transifex.txnative.TxNative
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.runBlocking
import org.librarysimplified.mdc.MDCKeys
import org.librarysimplified.r2.api.SR2Event
import org.librarysimplified.r2.api.SR2PageNumberingMode
import org.librarysimplified.r2.api.SR2ScrollingMode
import org.librarysimplified.r2.api.SR2Theme
import org.librarysimplified.r2.vanilla.SR2Controllers
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
import org.nypl.simplified.accessibility.AccessibilityServiceType
import org.nypl.simplified.books.book_registry.BookPreviewStatus
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.ui.thread.api.UIThreadServiceType
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.io.File
import java.io.IOException

class BookPreviewActivity : AppCompatActivity(R.layout.activity_book_preview) {

  companion object {

    private const val EXTRA_ENTRY = "org.nypl.simplified.viewer.preview.BookPreviewActivity.entry"

    fun startActivity(
      context: Activity,
      feedEntry: FeedEntry.FeedEntryOPDS
    ) {
      val intent = Intent(context, BookPreviewActivity::class.java)
      val bundle = Bundle().apply {
        this.putSerializable(EXTRA_ENTRY, feedEntry)
      }
      intent.putExtras(bundle)
      context.startActivity(intent)
    }
  }

  private val logger = LoggerFactory.getLogger(BookPreviewActivity::class.java)

  private val services =
    Services.serviceDirectory()
  private val accessibilityService =
    services.requireService(AccessibilityServiceType::class.java)
  private val uiThread =
    services.requireService(UIThreadServiceType::class.java)

  private val viewModel: BookPreviewViewModel by viewModels(
    factoryProducer = {
      BookPreviewViewModelFactory(
        services
      )
    }
  )

  private lateinit var feedEntry: FeedEntry.FeedEntryOPDS
  private lateinit var loadingProgress: ProgressBar
  private lateinit var previewContainer: FrameLayout

  private var fragmentNow: Fragment? = null
  private var subscriptions: CompositeDisposable = CompositeDisposable()
  private var readerOpened: Boolean = false
  private var file: File? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    this.loadingProgress = findViewById(R.id.loading_progress)
    this.previewContainer = findViewById(R.id.preview_container)

    ViewCompat.setOnApplyWindowInsetsListener(this.previewContainer) { view, insets ->
      val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        topMargin = bars.top
        leftMargin = bars.left
        rightMargin = bars.right
        bottomMargin = bars.bottom
      }
      WindowInsetsCompat.CONSUMED
    }

    this.feedEntry = intent.getSerializableExtra(EXTRA_ENTRY) as FeedEntry.FeedEntryOPDS

    MDC.put(MDCKeys.BOOK_TITLE, this.feedEntry.feedEntry.title)
    MDCKeys.put(MDCKeys.BOOK_PUBLISHER, this.feedEntry.feedEntry.publisher)
    MDC.remove(MDCKeys.BOOK_DRM)
    MDC.remove(MDCKeys.BOOK_FORMAT)

    this.viewModel.previewStatusLive.observe(this, this::onNewBookPreviewStatus)

    this.handleFeedEntry()
  }

  private var mAppCompatDelegate: AppCompatDelegate? = null
  override fun getDelegate(): AppCompatDelegate {
    if (mAppCompatDelegate == null) {
      mAppCompatDelegate = TxNative.wrapAppCompatDelegate(super.getDelegate(), this)
    }

    return mAppCompatDelegate!!
  }

  override fun onStop() {
    super.onStop()
    this.subscriptions.dispose()
  }

  @Deprecated("Deprecated in Java")
  override fun onBackPressed() {
    when (this.fragmentNow) {
      is SR2TOCFragment ->
        this.switchFragment(SR2ReaderFragment())

      is SR2SearchFragment ->
        this.switchFragment(SR2ReaderFragment())

      else -> {
        if (this.file?.exists() == true) {
          this.file?.delete()
        }
        super.onBackPressed()
      }
    }
  }

  private fun handleFeedEntry() {
    this.viewModel.handlePreviewStatus(this.feedEntry)
  }

  private fun switchFragment(fragment: Fragment) {
    this.fragmentNow = fragment
    this.supportFragmentManager.beginTransaction()
      .replace(R.id.preview_container, fragment)
      .commitAllowingStateLoss()
  }

  private fun onViewEventReceived(event: SR2ReaderViewEvent) {
    this.uiThread.checkIsUIThread()

    return when (event) {
      is SR2BookLoadingFailed ->
        this.handlePreviewDownloadFailed()

      is SR2ControllerBecameAvailable ->
        this.switchFragment(SR2ReaderFragment())

      is SR2ControllerBecameUnavailable -> {
        // Nothing to do
      }
    }
  }

  private fun onViewCommandReceived(command: SR2ReaderViewCommand) {
    this.uiThread.checkIsUIThread()

    return when (command) {
      SR2ReaderViewNavigationReaderClose ->
        this.onBackPressed()

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

  private fun onControllerEvent(event: SR2Event) {
    // Previews do not persist bookmarks or theme changes.
  }

  private fun onNewBookPreviewStatus(previewStatus: BookPreviewStatus) {
    when (previewStatus) {
      is BookPreviewStatus.HasPreview.Downloading -> {
        this.logger.debug(
          "book preview downloading: {} {} {}", previewStatus.currentTotalBytes,
          previewStatus.expectedTotalBytes, previewStatus.bytesPerSecond
        )
      }
      is BookPreviewStatus.HasPreview.DownloadFailed -> {
        this.logger.debug("book preview download failed")
        this.handlePreviewDownloadFailed()
      }
      is BookPreviewStatus.HasPreview.Ready.Embedded -> {
        this.logger.debug("embedded book preview")
        this.loadingProgress.isVisible = false
        this.supportFragmentManager.beginTransaction()
          .add(
            R.id.preview_container,
            BookPreviewEmbeddedFragment.newInstance(previewStatus.url.toString())
          )
          .commitAllowingStateLoss()
      }
      is BookPreviewStatus.HasPreview.Ready.BookPreview -> {
        this.logger.debug("book preview")
        this.loadingProgress.isVisible = false
        this.openReader(previewStatus.file)
      }
      is BookPreviewStatus.HasPreview.Ready.AudiobookPreview -> {
        this.logger.debug("audiobook preview")
        this.loadingProgress.isVisible = false
        this.openPlayer(previewStatus.file)
      }
      else -> {
        // do nothing
      }
    }
  }

  private fun handlePreviewDownloadFailed() {
    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.bookPreviewFailedTitle)
      .setMessage(R.string.bookPreviewFailedMessage)
      .setOnDismissListener { this.onBackPressed() }
      .create()
      .show()
  }

  private fun openPlayer(file: File) {
    this.file = file

    val audiobookPreviewPlayer = BookPreviewAudiobookFragment.newInstance(file, this.feedEntry)
    this.supportFragmentManager.beginTransaction()
      .add(R.id.preview_container, audiobookPreviewPlayer)
      .commitAllowingStateLoss()
  }

  private fun openReader(file: File) {
    if (this.readerOpened) {
      return
    }
    this.readerOpened = true
    this.file = file

    this.uiThread.checkIsUIThread()

    try {
      /*
       * Configure the global reader model. Previews have no DRM and no bookmarks; the controller
       * opens the start of the book itself.
       */

      SR2ReaderModel.scrollMode =
        if (this.accessibilityService.spokenFeedbackEnabled) {
          SR2ScrollingMode.SCROLLING_MODE_CONTINUOUS
        } else {
          SR2ScrollingMode.SCROLLING_MODE_PAGINATED
        }
      SR2ReaderModel.perChapterNumbering =
        SR2PageNumberingMode.WHOLE_BOOK

      this.subscriptions.add(SR2ReaderModel.controllerEvents.subscribe(this::onControllerEvent))
      this.subscriptions.add(SR2ReaderModel.viewCommands.subscribe(this::onViewCommandReceived))
      this.subscriptions.add(SR2ReaderModel.viewEvents.subscribe(this::onViewEventReceived))

      this.logger.debug("Opening asset...")
      val assetRetriever =
        AssetRetriever(
          contentResolver = this.contentResolver,
          httpClient = DefaultHttpClient()
        )

      val rawBookAsset =
        when (val a = runBlocking { assetRetriever.retrieve(file) }) {
          is Try.Failure -> throw IOException(a.value.message)
          is Try.Success -> a.value
        }

      SR2ReaderModel.controllerCreate(
        context = this.application,
        contentProtections = listOf(),
        bookFile = rawBookAsset,
        bookId = this.feedEntry.feedEntry.id,
        theme = SR2Theme(),
        controllers = SR2Controllers(),
        bookmarks = listOf(),
        allowCopyPaste = true
      )
    } catch (e: Exception) {
      this.logger.error("Failed to open preview reader: ", e)
      this.handlePreviewDownloadFailed()
    }
  }
}
