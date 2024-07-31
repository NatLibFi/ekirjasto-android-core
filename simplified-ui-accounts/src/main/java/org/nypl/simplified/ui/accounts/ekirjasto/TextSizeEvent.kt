package org.nypl.simplified.ui.accounts.ekirjasto
sealed class TextSizeEvent {

  object TextSizeSmall : TextSizeEvent()

  object TextSizeMedium : TextSizeEvent()

  object TextSizeLarge : TextSizeEvent()

  object TextSizeExtraLarge : TextSizeEvent()

  object TextSizeExtraExtraLarge : TextSizeEvent()

}
