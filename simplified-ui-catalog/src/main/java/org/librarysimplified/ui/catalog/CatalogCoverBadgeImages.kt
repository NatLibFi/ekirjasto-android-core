package org.librarysimplified.ui.catalog

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.nypl.simplified.books.book_database.api.BookFormats.BookFormatDefinition.BOOK_FORMAT_AUDIO
import org.nypl.simplified.books.book_database.api.BookFormats.BookFormatDefinition.BOOK_FORMAT_EPUB
import org.nypl.simplified.books.book_database.api.BookFormats.BookFormatDefinition.BOOK_FORMAT_PDF
import org.nypl.simplified.books.covers.BookCoverBadge
import org.nypl.simplified.books.covers.BookCoverBadgeLookupType
import org.nypl.simplified.feeds.api.FeedEntry.FeedEntryOPDS
import org.nypl.simplified.ui.screen.ScreenSizeInformationType

/**
 * The images used to add badges to book covers.
 */

class CatalogCoverBadgeImages private constructor(
  private val screenSize: ScreenSizeInformationType,
  private val backgroundColorRGBA: () -> Int,
  private val borderColorRGBA: () -> Int,
  private val audioBookIcon: Bitmap
) : BookCoverBadgeLookupType {

  override fun badgeForEntry(
    entry: FeedEntryOPDS
  ): BookCoverBadge? {
    return when (entry.probableFormat) {
      BOOK_FORMAT_EPUB -> {
        null
      }
      BOOK_FORMAT_AUDIO -> {
        BookCoverBadge(
          bitmap = this.audioBookIcon,
          width = this.screenSize.dpToPixels(24).toInt(),
          height = this.screenSize.dpToPixels(24).toInt(),
          borderWidth = this.screenSize.dpToPixels(2).toInt(),
          offsetSize = this.screenSize.dpToPixels(6).toInt(),
          backgroundColorRGBA = { this.backgroundColorRGBA() },
          borderColorRGBA = { this.borderColorRGBA() }
        )
      }
      BOOK_FORMAT_PDF -> {
        null
      }
      null -> {
        null
      }
    }
  }

  companion object {

    /**
     * Create a new set of badge images.
     */

    fun create(
      resources: Resources,
      backgroundColorRGBA: () -> Int,
      borderColorRGBA: () -> Int,
      screenSize: ScreenSizeInformationType
    ): BookCoverBadgeLookupType {
      val audioBookIcon = BitmapFactory.decodeResource(resources, R.drawable.audiobook_icon)
      return CatalogCoverBadgeImages(
        audioBookIcon = audioBookIcon,
        backgroundColorRGBA = backgroundColorRGBA,
        borderColorRGBA = borderColorRGBA,
        screenSize = screenSize
      )
    }
  }
}