package org.librarysimplified.viewer.audiobook

import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.reactivex.disposables.CompositeDisposable
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventError
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventChapterWaiting
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackBuffering
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackPaused
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackPreparing
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackProgressUpdate
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackStarted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackStopped
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackWaitingForAction
import org.librarysimplified.audiobook.api.PlayerPauseReason
import org.librarysimplified.audiobook.api.PlayerSleepTimer
import org.librarysimplified.audiobook.api.PlayerSleepTimerEvent
import org.librarysimplified.audiobook.api.PlayerSleepTimerEvent.PlayerSleepTimerFinished
import org.librarysimplified.audiobook.api.PlayerSleepTimerEvent.PlayerSleepTimerStatusChanged
import org.librarysimplified.audiobook.manifest.api.PlayerManifestPositionMetadata
import org.librarysimplified.audiobook.manifest.api.PlayerMillisecondsAbsolute
import org.librarysimplified.audiobook.views.PlayerModel
import org.librarysimplified.audiobook.views.PlayerTimeStrings
import org.librarysimplified.audiobook.views.PlayerViewCommand
import org.librarysimplified.services.api.Services
import org.nypl.simplified.books.covers.BookCoverProviderType
import org.nypl.simplified.feeds.api.FeedEntry
import org.slf4j.LoggerFactory

/**
 * The e-kirjasto audiobook player screen.
 *
 * This is a re-skin of the audiobook 24.0.0 stock `PlayerFragment`: it drives the same global
 * [PlayerModel] but inflates e-kirjasto's custom layout (top + bottom toolbars, back button) and
 * uses e-kirjasto's view ids. Unlike the old (audiobook 12.x) fragment, it does NOT do its own
 * seekbar/playback-speed bookkeeping, audio focus, or media-session handling — the 24.x player
 * reports accurate positions via [PlayerManifestPositionMetadata] and the library's
 * focus/bluetooth/media-control watchers handle the rest.
 */

class EkirjaPlayerFragment : Fragment(R.layout.ekirjasto_audio_player_view) {

  private val logger =
    LoggerFactory.getLogger(EkirjaPlayerFragment::class.java)

  private lateinit var toolbar: Toolbar
  private lateinit var bottomToolbar: BottomNavigationView
  private lateinit var backButton: LinearLayout
  private lateinit var coverView: ImageView
  private lateinit var bookmarkView: ImageView
  private lateinit var titleView: TextView
  private lateinit var authorView: TextView
  private lateinit var chapterView: TextView
  private lateinit var remainingBookTimeView: TextView
  private lateinit var playPauseButton: ImageView
  private lateinit var skipForwardButton: ImageView
  private lateinit var skipBackwardButton: ImageView
  private lateinit var seekBar: SeekBar
  private lateinit var timeCurrentView: TextView
  private lateinit var timeRemainingView: TextView
  private lateinit var commandsView: View
  private lateinit var downloadingView: ProgressBar
  private lateinit var waitingView: TextView

  private lateinit var menuPlaybackRate: MenuItem
  private lateinit var menuSleep: MenuItem
  private lateinit var menuTOC: MenuItem
  private lateinit var menuAddBookmark: MenuItem

  private var subscriptions = CompositeDisposable()
  private var seekDragging = false

  /*
   * The minimum (lower bound) of the current reading-order item on the absolute timeline. The
   * seek bar works in offsets relative to this. Updated on every position event.
   */

  @Volatile
  private var positionMin: PlayerMillisecondsAbsolute = PlayerMillisecondsAbsolute(0)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    this.toolbar = view.findViewById(R.id.audioBookToolbar)
    this.bottomToolbar = view.findViewById(R.id.audioBookBottomToolbar)
    this.backButton = view.findViewById(R.id.backButton)
    this.coverView = view.findViewById(R.id.player_cover)
    this.bookmarkView = view.findViewById(R.id.player_bookmark)
    this.titleView = view.findViewById(R.id.player_title)
    this.authorView = view.findViewById(R.id.player_author)
    this.chapterView = view.findViewById(R.id.player_spine_element)
    this.remainingBookTimeView = view.findViewById(R.id.player_remaining_book_time)
    this.playPauseButton = view.findViewById(R.id.player_play_button)
    this.skipForwardButton = view.findViewById(R.id.player_jump_forwards)
    this.skipBackwardButton = view.findViewById(R.id.player_jump_backwards)
    this.seekBar = view.findViewById(R.id.player_progress)
    this.timeCurrentView = view.findViewById(R.id.player_time)
    this.timeRemainingView = view.findViewById(R.id.player_time_maximum)
    this.commandsView = view.findViewById(R.id.player_commands)
    this.downloadingView = view.findViewById(R.id.player_downloading_chapter)
    this.waitingView = view.findViewById(R.id.player_waiting_buffering)

    /*
     * Apply window insets so the player doesn't overlap the system bars.
     */

    ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
      val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      v.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> {
        topMargin = bars.top
        leftMargin = bars.left
        rightMargin = bars.right
        bottomMargin = bars.bottom
      }
      WindowInsetsCompat.CONSUMED
    }

    this.bookmarkView.alpha = 0.0f
    this.waitingView.text = ""
    this.seekBar.isEnabled = false

    this.backButton.setOnClickListener {
      PlayerModel.submitViewCommand(PlayerViewCommand.PlayerViewNavigationCloseAll)
    }
    this.playPauseButton.setOnClickListener {
      PlayerModel.playOrPauseAsAppropriate(PlayerPauseReason.PAUSE_REASON_USER_EXPLICITLY_PAUSED)
    }
    this.skipForwardButton.setOnClickListener { PlayerModel.skipForward() }
    this.skipBackwardButton.setOnClickListener { PlayerModel.skipBack() }
    this.seekBar.setOnTouchListener { _, event -> this.handleTouchOnSeekbar(event) }

    this.toolbar.setNavigationOnClickListener {
      PlayerModel.submitViewCommand(PlayerViewCommand.PlayerViewNavigationCloseAll)
    }

    this.configureToolbarMenus()

    this.titleView.text = PlayerModel.bookTitle
    this.authorView.text = PlayerModel.bookAuthor
    this.loadCoverImage()
  }

  /**
   * Load the book cover directly into the cover view. This uses the cover provider's own async
   * loader (with placeholder handling), which is reliable on first open — unlike reading the
   * (possibly not-yet-set) PlayerModel.coverImage bitmap. The activity separately sets
   * PlayerModel.coverImage for the media notification.
   */

  private fun loadCoverImage() {
    val parameters = AudioBookViewerModel.parameters ?: return
    try {
      val covers =
        Services.serviceDirectory().requireService(BookCoverProviderType::class.java)
      covers.loadCoverInto(
        entry = FeedEntry.FeedEntryOPDS(parameters.accountID, parameters.opdsEntry),
        hasBadge = false,
        imageView = this.coverView,
        width = 0,
        height = 0
      )
    } catch (e: Exception) {
      this.logger.error("could not load cover image: ", e)
    }
  }

  override fun onStart() {
    super.onStart()

    this.subscriptions = CompositeDisposable()
    this.subscriptions.add(PlayerModel.playerEvents.subscribe(this::onPlayerEvent))
    this.subscriptions.add(PlayerSleepTimer.events.subscribe(this::onSleepTimerEvent))
    this.setPlayPauseButtonAppropriately()
  }

  override fun onStop() {
    super.onStop()
    this.subscriptions.dispose()
  }

  private fun configureToolbarMenus() {
    this.toolbar.inflateMenu(R.menu.top_toolbar_menu)
    this.bottomToolbar.inflateMenu(R.menu.bottom_toolbar_menu)

    this.menuTOC = this.toolbar.menu.findItem(R.id.player_menu_toc)
    this.menuTOC.setOnMenuItemClickListener {
      PlayerModel.submitViewCommand(PlayerViewCommand.PlayerViewNavigationTOCOpen)
      true
    }

    this.menuPlaybackRate = this.bottomToolbar.menu.findItem(R.id.player_menu_playback_rate)
    this.menuPlaybackRate.setOnMenuItemClickListener {
      PlayerModel.submitViewCommand(PlayerViewCommand.PlayerViewNavigationPlaybackRateMenuOpen)
      true
    }

    /*
     * Playback rate changes have no effect below API 23.
     */

    if (Build.VERSION.SDK_INT < 23) {
      this.menuPlaybackRate.isVisible = false
    }

    this.menuSleep = this.bottomToolbar.menu.findItem(R.id.player_menu_sleep)
    this.menuSleep.setOnMenuItemClickListener {
      PlayerModel.submitViewCommand(PlayerViewCommand.PlayerViewNavigationSleepMenuOpen)
      true
    }

    this.menuAddBookmark = this.bottomToolbar.menu.findItem(R.id.player_menu_add_bookmark)
    this.menuAddBookmark.setOnMenuItemClickListener {
      PlayerModel.bookmarkCreate()
      true
    }
  }

  private fun setPlayPauseButtonAppropriately() {
    if (PlayerModel.isPlaying) {
      this.setButtonToShowPause()
    } else {
      this.setButtonToShowPlay()
    }
  }

  private fun setButtonToShowPause() {
    this.playPauseButton.setImageResource(R.drawable.elibrary_pause_round_icon)
    this.playPauseButton.contentDescription = this.getString(R.string.audiobook_accessibility_pause)
    this.playPauseButton.setOnClickListener {
      PlayerModel.pause(PlayerPauseReason.PAUSE_REASON_USER_EXPLICITLY_PAUSED)
    }
  }

  private fun setButtonToShowPlay() {
    this.playPauseButton.setImageResource(R.drawable.elibrary_play_icon)
    this.playPauseButton.contentDescription = this.getString(R.string.audiobook_accessibility_play)
    this.playPauseButton.setOnClickListener {
      PlayerModel.play()
    }
  }

  private fun onPlayerEvent(event: PlayerEvent) {
    when (event) {
      is PlayerEventPlaybackStarted -> {
        this.showCommands()
        this.setButtonToShowPause()
        this.seekBar.isEnabled = true
        this.onEventUpdateTimeRelatedUI(event.positionMetadata)
      }
      is PlayerEventPlaybackProgressUpdate -> {
        this.showCommands()
        this.onEventUpdateTimeRelatedUI(event.positionMetadata)
      }
      is PlayerEventPlaybackPaused -> {
        this.showCommands()
        this.setButtonToShowPlay()
        this.onEventUpdateTimeRelatedUI(event.positionMetadata)
      }
      is PlayerEventPlaybackStopped -> {
        this.showCommands()
        this.setButtonToShowPlay()
        this.onEventUpdateTimeRelatedUI(event.positionMetadata)
      }
      is PlayerEventPlaybackBuffering -> {
        this.showWaiting(this.getString(R.string.audiobook_player_buffering))
        this.onEventUpdateTimeRelatedUI(event.positionMetadata)
      }
      is PlayerEventPlaybackPreparing -> {
        this.showWaiting(this.getString(R.string.audiobook_player_buffering))
        this.onEventUpdateTimeRelatedUI(event.positionMetadata)
      }
      is PlayerEventChapterWaiting -> {
        this.showWaiting(
          this.getString(R.string.audiobook_player_waiting, event.readingOrderItem.index + 1)
        )
        this.onEventUpdateTimeRelatedUI(event.positionMetadata)
      }
      is PlayerEventPlaybackWaitingForAction -> {
        this.showCommands()
        this.setButtonToShowPlay()
        this.onEventUpdateTimeRelatedUI(event.positionMetadata)
      }
      is PlayerEventError -> {
        this.showWaiting(this.getString(R.string.audiobook_player_error, event.errorCodeName, event.errorCode))
      }
      is PlayerEvent.PlayerEventWithPosition.PlayerEventChapterCompleted,
      is PlayerEvent.PlayerEventWithPosition.PlayerEventCreateBookmark,
      is PlayerEvent.PlayerEventDeleteBookmark,
      is PlayerEvent.PlayerEventManifestUpdated,
      is PlayerEvent.PlayerEventPlaybackRateChanged,
      is PlayerEvent.PlayerAccessibilityEvent -> {
        // Nothing to do
      }
    }
  }

  private fun showCommands() {
    this.downloadingView.visibility = GONE
    this.commandsView.visibility = VISIBLE
    this.waitingView.text = ""
  }

  private fun showWaiting(message: String) {
    this.downloadingView.visibility = VISIBLE
    this.commandsView.visibility = INVISIBLE
    this.waitingView.text = message
  }

  private fun handleTouchOnSeekbar(event: MotionEvent?): Boolean {
    return when (event?.action) {
      MotionEvent.ACTION_DOWN -> {
        this.seekDragging = true
        this.seekBar.onTouchEvent(event)
      }
      MotionEvent.ACTION_UP -> {
        if (this.seekDragging) {
          this.seekDragging = false
          val newOffset =
            PlayerMillisecondsAbsolute(this.positionMin.value + this.seekBar.progress.toLong())
          PlayerModel.movePlayheadToAbsoluteTime(newOffset)
        }
        this.seekBar.onTouchEvent(event)
      }
      MotionEvent.ACTION_CANCEL -> {
        this.seekDragging = false
        this.seekBar.onTouchEvent(event)
      }
      else ->
        this.seekBar.onTouchEvent(event)
    }
  }

  private fun onEventUpdateTimeRelatedUI(positionMetadata: PlayerManifestPositionMetadata) {
    val lower = positionMetadata.tocItem.intervalAbsoluteMilliseconds.lower()
    val upperRelative = positionMetadata.tocItem.intervalAbsoluteMilliseconds.size().value.toInt()
    val progress = positionMetadata.tocItemPosition.millis.toInt()

    this.positionMin = lower
    this.seekBar.max = upperRelative
    this.seekBar.isEnabled = true
    if (!this.seekDragging) {
      this.seekBar.progress = progress
    }

    this.chapterView.text = positionMetadata.tocItem.title
    this.titleView.text = PlayerModel.bookTitle
    this.authorView.text = PlayerModel.bookAuthor

    this.remainingBookTimeView.text =
      PlayerTimeStrings.remainingBookTime(this.requireContext(), positionMetadata.totalRemainingBookTime)
    this.timeRemainingView.text =
      PlayerTimeStrings.remainingTOCItemTime(positionMetadata.tocItemRemaining)
    this.timeCurrentView.text =
      PlayerTimeStrings.elapsedTOCItemTime(positionMetadata.tocItemPosition)
  }

  private fun onSleepTimerEvent(event: PlayerSleepTimerEvent) {
    when (event) {
      PlayerSleepTimerFinished -> {
        // Nothing to do here; the player pauses itself.
      }
      is PlayerSleepTimerStatusChanged -> {
        // The sleep menu's countdown text is managed by the stock sleep dialog; nothing required.
      }
    }
  }
}
