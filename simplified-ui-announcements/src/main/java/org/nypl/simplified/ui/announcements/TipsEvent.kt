package org.nypl.simplified.ui.announcements

sealed class TipsEvent {
  object DismissTips : TipsEvent()
}
