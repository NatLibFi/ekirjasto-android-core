package org.librarysimplified.viewer.audiobook

import androidx.fragment.app.Fragment

/**
 * A fragment that displays an indeterminate progress indicator while the audiobook player is
 * loading. With audiobook 24.0.0, the actual work (manifest download, license checks, engine
 * startup) happens inside [org.librarysimplified.audiobook.views.PlayerModel]; the hosting
 * activity observes the model state and swaps this fragment out for the player once the book is
 * open.
 */

class AudioBookLoadingFragment : Fragment(R.layout.audio_book_player_loading)
