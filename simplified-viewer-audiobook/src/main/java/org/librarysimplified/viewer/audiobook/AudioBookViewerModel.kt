package org.librarysimplified.viewer.audiobook

/**
 * A trivial singleton used to hand the audiobook player parameters from the viewer entry point
 * ([AudioBookViewer]) to the player activity. The audiobook 24.0.0 player keeps the actual player
 * state in the global [org.librarysimplified.audiobook.views.PlayerModel]; this object only needs
 * to carry the book parameters across the activity boundary (they are too large/complex to pass
 * safely as intent extras).
 */

internal object AudioBookViewerModel {

  @Volatile
  internal var parameters: AudioBookPlayerParameters? = null
}
