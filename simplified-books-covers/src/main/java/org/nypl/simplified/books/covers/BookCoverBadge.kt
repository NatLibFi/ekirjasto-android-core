package org.nypl.simplified.books.covers

import android.graphics.Bitmap

/**
 * A book badge definition.
 */

data class BookCoverBadge(
  val bitmap: Bitmap,
  val width: Int,
  val height: Int,
  val borderWidth: Int,
  val offsetSize: Int,

  /**
   * A function that, when evaluated, returns an RGB color.
   */

  val backgroundColorRGBA: () -> Int,
  val borderColorRGBA: () -> Int
)
