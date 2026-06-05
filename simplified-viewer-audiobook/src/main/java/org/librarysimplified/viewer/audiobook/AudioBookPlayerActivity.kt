package org.librarysimplified.viewer.audiobook

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.transifex.txnative.TxNative
import io.reactivex.disposables.CompositeDisposable
import org.librarysimplified.audiobook.api.PlayerAuthorizationHandlerNoOp
import org.librarysimplified.audiobook.api.PlayerBookID
import org.librarysimplified.audiobook.api.PlayerBookmark
import org.librarysimplified.audiobook.api.PlayerBookmarkKind
import org.librarysimplified.audiobook.api.PlayerBookmarkMetadata
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
import org.librarysimplified.audiobook.manifest.api.PlayerManifestPositionMetadata
import org.librarysimplified.audiobook.manifest.api.PlayerMillisecondsReadingOrderItem
import org.librarysimplified.audiobook.views.PlayerBookmarkModel
import org.librarysimplified.audiobook.views.PlayerModel
import org.librarysimplified.audiobook.views.PlayerModelState
import org.librarysimplified.audiobook.views.PlayerPlaybackRateFragment
import org.librarysimplified.audiobook.views.PlayerSleepTimerFragment
import org.librarysimplified.audiobook.views.PlayerTOCFragment
import org.librarysimplified.audiobook.views.PlayerViewCommand
import org.librarysimplified.audiobook.views.bluetooth.PlayerBluetoothWatcher
import org.librarysimplified.audiobook.views.focus.PlayerFocusWatcher
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.mdc.MDCKeys
import org.librarysimplified.services.api.Services
import org.nypl.simplified.bookmarks.api.BookmarkServiceType
import org.nypl.simplified.books.api.bookmark.Bookmark
import org.nypl.simplified.books.covers.BookCoverProviderType
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.books.api.playerCredentials
import org.nypl.simplified.books.time.tracking.TimeTrackingServiceType
import org.nypl.simplified.opds.core.getOrNull
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The activity that hosts the audio book player.
 *
 * As of audiobook 24.0.0, the player itself is owned by the global [PlayerModel]. This activity is
 * a thin host: it observes the model's state and view-command streams, drives the manifest/LCP
 * open via [PlayerModel.openPlayerForManifest], swaps between the loading/player/TOC fragments,
 * and bridges player bookmark/time-tracking events to e-kirjasto's services.
 */

class AudioBookPlayerActivity : AppCompatActivity() {

  private val log: Logger =
    LoggerFactory.getLogger(AudioBookPlayerActivity::class.java)

  private lateinit var bookmarkService: BookmarkServiceType
  private lateinit var bookCoverProvider: BookCoverProviderType
  private lateinit var profiles: ProfilesControllerType
  private lateinit var timeTrackingService: TimeTrackingServiceType
  private lateinit var httpClient: LSHTTPClientType

  private var fragmentNow: Fragment = AudioBookLoadingFragment()
  private var subscriptions = CompositeDisposable()

  /**
   * The most recent playback position, cached from player events so it can be persisted as a
   * last-read bookmark when the player is left. The player only emits last-read bookmarks
   * periodically, so without this the resume point would be coarse.
   */

  @Volatile
  private var lastReadPosition: PlayerBookmark? = null

  private var mAppCompatDelegate: AppCompatDelegate? = null

  override fun getDelegate(): AppCompatDelegate {
    val existing = this.mAppCompatDelegate
    if (existing != null) {
      return existing
    }
    val created = TxNative.wrapAppCompatDelegate(super.getDelegate(), this)
    this.mAppCompatDelegate = created
    return created
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    this.log.debug("onCreate")
    super.onCreate(null)

    val services = Services.serviceDirectory()
    this.bookmarkService =
      services.requireService(BookmarkServiceType::class.java)
    this.bookCoverProvider =
      services.requireService(BookCoverProviderType::class.java)
    this.profiles =
      services.requireService(ProfilesControllerType::class.java)
    this.timeTrackingService =
      services.requireService(TimeTrackingServiceType::class.java)
    this.httpClient =
      services.requireService(LSHTTPClientType::class.java)

    this.setContentView(R.layout.audio_book_player_base)

    val parameters = AudioBookViewerModel.parameters
    if (parameters != null) {
      MDC.put(MDCKeys.ACCOUNT_INTERNAL_ID, parameters.accountID.uuid.toString())
      MDC.put(MDCKeys.ACCOUNT_PROVIDER_ID, parameters.accountProviderID.toString())
      MDC.put(MDCKeys.BOOK_INTERNAL_ID, parameters.bookID.value())
      MDC.put(MDCKeys.BOOK_TITLE, parameters.opdsEntry.title)
    }

    this.switchFragment(AudioBookLoadingFragment())
  }

  override fun onStart() {
    super.onStart()

    PlayerBluetoothWatcher.enable(this.application)
    PlayerFocusWatcher.enable(this.application)

    this.subscriptions = CompositeDisposable()
    this.subscriptions.add(PlayerModel.stateEvents.subscribe(this::onModelStateEvent))
    this.subscriptions.add(PlayerModel.viewCommands.subscribe(this::onPlayerViewCommand))
    this.subscriptions.add(PlayerModel.playerEvents.subscribe(this::onPlayerEvent))
  }

  override fun onStop() {
    super.onStop()
    this.saveLastReadPosition()
    this.subscriptions.dispose()
  }

  private fun close() {
    try {
      PlayerModel.closeBookOrDismissError()
    } catch (e: Exception) {
      this.log.debug("failed to close book: ", e)
    }
    try {
      this.timeTrackingService.stopTracking()
    } catch (e: Exception) {
      this.log.debug("failed to stop time tracking: ", e)
    }
    this.finish()
  }

  @Deprecated("Deprecated in Java")
  override fun onBackPressed() {
    return when (this.fragmentNow) {
      is PlayerTOCFragment -> this.switchFragment(EkirjaPlayerFragment())
      else -> this.close()
    }
  }

  private fun onModelStateEvent(state: PlayerModelState) {
    val parameters = AudioBookViewerModel.parameters ?: return

    when (state) {
      is PlayerModelState.PlayerManifestOK -> {
        /*
         * Work around an audiobook 24.0.0 bug: for a packaged book source,
         * ExoAudioBookProvider.create() copies the book into
         * files/exoplayer_audio/<bookID>/book.zip but never creates that directory first
         * (ExoAudioBook.findDirectoryFor() only builds the path). Palace's own app rarely hits
         * this because current checkouts use the license-file source; e-kirjasto always uses
         * packaged LCP audiobooks, so we must ensure the directory exists.
         */

        try {
          val bookID =
            PlayerBookID.transform(state.manifest.metadata.identifier)
          File(File(this.application.filesDir, "exoplayer_audio"), bookID.value).mkdirs()
        } catch (e: Exception) {
          this.log.warn("could not pre-create exoplayer_audio directory: ", e)
        }

        PlayerModel.openPlayerForManifest(
          context = this.application,
          httpClient = this.httpClient,
          manifest = state.manifest,
          playerID = parameters.playerID,
          fetchAll = true,
          bookCredentials = parameters.drmInfo.playerCredentials(),
          bookSource = state.bookSource,
          authorizationHandler = PlayerAuthorizationHandlerNoOp
        )
      }

      is PlayerModelState.PlayerOpen -> {
        this.loadCoverImage()
        this.restoreBookmarks(state)
        this.startTimeTracking(parameters)
        this.switchFragment(EkirjaPlayerFragment())
      }

      PlayerModelState.PlayerManifestInProgress -> {
        this.switchFragment(AudioBookLoadingFragment())
      }

      PlayerModelState.PlayerClosed -> {
        this.timeTrackingService.stopTracking()
        this.finish()
      }

      is PlayerModelState.PlayerBookOpenFailed ->
        this.showErrorAndFinish(state.message)

      is PlayerModelState.PlayerManifestDownloadFailed ->
        this.showErrorAndFinish(getString(R.string.audio_book_manifest_download_error))

      is PlayerModelState.PlayerManifestParseFailed ->
        this.showErrorAndFinish(getString(R.string.audio_book_manifest_parse_error))

      is PlayerModelState.PlayerManifestLicenseChecksFailed ->
        this.showErrorAndFinish(getString(R.string.audio_book_manifest_license_error))
    }
  }

  private fun startTimeTracking(parameters: AudioBookPlayerParameters) {
    this.timeTrackingService.startTimeTracking(
      accountID = parameters.accountID,
      bookId = parameters.opdsEntry.id,
      libraryId = parameters.accountProviderID.toString(),
      timeTrackingUri = parameters.opdsEntry.timeTrackingUri.getOrNull()
    )
  }

  private fun loadCoverImage() {
    val parameters = AudioBookViewerModel.parameters ?: return
    try {
      this.bookCoverProvider.loadCoverAsBitmap(
        FeedEntry.FeedEntryOPDS(parameters.accountID, parameters.opdsEntry),
        { bitmap -> PlayerModel.setCoverImage(bitmap) },
        R.drawable.main_icon
      )
    } catch (e: Exception) {
      this.log.error("could not load cover image: ", e)
    }
  }

  private fun restoreBookmarks(state: PlayerModelState.PlayerOpen) {
    val parameters = AudioBookViewerModel.parameters ?: return
    try {
      val raw =
        this.bookmarkService.bookmarkSyncAndLoad(parameters.accountID, parameters.bookID)
          .get(15L, TimeUnit.SECONDS)

      val explicit =
        raw.bookmarks.filterIsInstance<Bookmark.AudiobookBookmark>()
          .map(AudioBookBookmarks::toPlayerBookmark)
      PlayerBookmarkModel.setBookmarks(explicit)

      val lastRead = raw.lastReadLocal as? Bookmark.AudiobookBookmark
      if (state.positionOnOpen == null && lastRead != null) {
        this.log.debug("restoring last-read position from bookmark")
        PlayerModel.movePlayheadTo(AudioBookBookmarks.toPlayerBookmark(lastRead).position)
      }
    } catch (e: Exception) {
      this.log.error("could not load/restore bookmarks: ", e)
    }
  }

  private fun onPlayerEvent(event: PlayerEvent) {
    this.timeTrackingService.onPlayerEventReceived(event)
    this.rememberLastReadPosition(event)

    when (event) {
      is PlayerEvent.PlayerEventWithPosition.PlayerEventCreateBookmark ->
        this.onCreateBookmark(event)
      is PlayerEvent.PlayerEventDeleteBookmark ->
        this.onDeleteBookmark(event)
      is PlayerEvent.PlayerEventPlaybackRateChanged ->
        this.onPlaybackRateChanged(event)
      else -> {
        // Nothing to do
      }
    }
  }

  /**
   * Cache the most recent playback position from position-bearing player events.
   */

  private fun rememberLastReadPosition(event: PlayerEvent) {
    val bookmark =
      when (event) {
        is PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackProgressUpdate ->
          this.lastReadBookmarkOf(event.readingOrderItem, event.offsetMilliseconds, event.positionMetadata)
        is PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackStarted ->
          this.lastReadBookmarkOf(event.readingOrderItem, event.offsetMilliseconds, event.positionMetadata)
        is PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackPaused ->
          this.lastReadBookmarkOf(event.readingOrderItem, event.readingOrderItemOffsetMilliseconds, event.positionMetadata)
        is PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackStopped ->
          this.lastReadBookmarkOf(event.readingOrderItem, event.readingOrderItemOffsetMilliseconds, event.positionMetadata)
        else ->
          null
      }
    if (bookmark != null) {
      this.lastReadPosition = bookmark
    }
  }

  private fun lastReadBookmarkOf(
    item: PlayerReadingOrderItemType,
    offset: PlayerMillisecondsReadingOrderItem,
    metadata: PlayerManifestPositionMetadata
  ): PlayerBookmark {
    return PlayerBookmark(
      kind = PlayerBookmarkKind.LAST_READ,
      readingOrderID = item.id,
      offsetMilliseconds = offset,
      metadata = PlayerBookmarkMetadata.fromPositionMetadata(metadata)
    )
  }

  /**
   * Persist the cached last-read position as a last-read bookmark. Called when the player screen
   * is left so that reopening the book resumes at the exact stopping point rather than the last
   * periodic bookmark.
   */

  private fun saveLastReadPosition() {
    val parameters = AudioBookViewerModel.parameters ?: return
    val position = this.lastReadPosition ?: return
    try {
      this.bookmarkService.bookmarkCreate(
        accountID = parameters.accountID,
        bookmark = AudioBookBookmarks.fromPlayerBookmark(
          opdsId = parameters.opdsEntry.id,
          deviceID = AudioBookDevices.deviceId(this.profiles, parameters.bookID),
          source = position
        ),
        ignoreRemoteFailures = true
      )
    } catch (e: Exception) {
      this.log.error("could not save last-read position: ", e)
    }
  }

  private fun onCreateBookmark(
    event: PlayerEvent.PlayerEventWithPosition.PlayerEventCreateBookmark
  ) {
    val parameters = AudioBookViewerModel.parameters ?: return

    val playerBookmark =
      PlayerBookmark(
        kind = event.kind,
        readingOrderID = event.readingOrderItem.id,
        offsetMilliseconds = event.readingOrderItemOffsetMilliseconds,
        metadata = event.bookmarkMetadata
      )

    try {
      this.bookmarkService.bookmarkCreate(
        accountID = parameters.accountID,
        bookmark = AudioBookBookmarks.fromPlayerBookmark(
          opdsId = parameters.opdsEntry.id,
          deviceID = AudioBookDevices.deviceId(this.profiles, parameters.bookID),
          source = playerBookmark
        ),
        ignoreRemoteFailures = true
      )
    } catch (e: Exception) {
      this.log.error("could not create bookmark: ", e)
    }

    if (event.kind == PlayerBookmarkKind.EXPLICIT) {
      val newBookmarks = arrayListOf<PlayerBookmark>()
      newBookmarks.addAll(PlayerBookmarkModel.bookmarks())
      newBookmarks.removeIf { b -> b.position == playerBookmark.position }
      newBookmarks.add(0, playerBookmark)
      PlayerBookmarkModel.setBookmarks(newBookmarks.toList())
      Toast.makeText(this, R.string.audio_book_player_bookmark_added, Toast.LENGTH_SHORT).show()
    }
  }

  private fun onDeleteBookmark(
    event: PlayerEvent.PlayerEventDeleteBookmark
  ) {
    val parameters = AudioBookViewerModel.parameters ?: return

    try {
      this.bookmarkService.bookmarkDelete(
        accountID = parameters.accountID,
        bookmark = AudioBookBookmarks.fromPlayerBookmark(
          opdsId = parameters.opdsEntry.id,
          deviceID = AudioBookDevices.deviceId(this.profiles, parameters.bookID),
          source = event.bookmark
        ),
        ignoreRemoteFailures = true
      )
    } catch (e: Exception) {
      this.log.error("could not delete bookmark: ", e)
    }

    val newBookmarks = arrayListOf<PlayerBookmark>()
    newBookmarks.addAll(PlayerBookmarkModel.bookmarks())
    newBookmarks.remove(event.bookmark)
    PlayerBookmarkModel.setBookmarks(newBookmarks.toList())
  }

  private fun onPlaybackRateChanged(
    event: PlayerEvent.PlayerEventPlaybackRateChanged
  ) {
    val parameters = AudioBookViewerModel.parameters ?: return
    this.profiles.profileUpdate { description ->
      description.copy(
        preferences = description.preferences.copy(
          playbackRates = description.preferences.playbackRates.plus(
            Pair(parameters.bookID.value(), event.rate)
          )
        )
      )
    }
  }

  private fun onPlayerViewCommand(command: PlayerViewCommand) {
    when (command) {
      PlayerViewCommand.PlayerViewNavigationTOCOpen ->
        this.switchFragment(PlayerTOCFragment())
      PlayerViewCommand.PlayerViewNavigationTOCClose ->
        this.switchFragment(EkirjaPlayerFragment())
      PlayerViewCommand.PlayerViewNavigationSleepMenuOpen ->
        this.popupFragment(PlayerSleepTimerFragment())
      PlayerViewCommand.PlayerViewNavigationPlaybackRateMenuOpen ->
        this.popupFragment(PlayerPlaybackRateFragment())
      PlayerViewCommand.PlayerViewNavigationCloseAll ->
        this.close()
      PlayerViewCommand.PlayerViewCoverImageChanged,
      PlayerViewCommand.PlayerViewErrorsDownloadOpen,
      PlayerViewCommand.PlayerViewLoginOpen -> {
        // Nothing to do
      }
    }
  }

  private fun showErrorAndFinish(message: String) {
    if (this.isFinishing || this.isDestroyed) {
      return
    }
    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.audio_book_player_error_book_open)
      .setMessage(message)
      .setOnDismissListener { this.close() }
      .show()
  }

  private fun switchFragment(fragment: Fragment) {
    this.fragmentNow = fragment
    this.supportFragmentManager.beginTransaction()
      .replace(R.id.audio_book_player_fragment_holder, fragment)
      .commitAllowingStateLoss()
  }

  private fun popupFragment(fragment: DialogFragment) {
    fragment.show(this.supportFragmentManager, fragment.tag)
  }
}
