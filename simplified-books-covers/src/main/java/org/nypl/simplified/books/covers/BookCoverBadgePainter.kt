package org.nypl.simplified.books.covers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.squareup.picasso.Transformation
import org.nypl.simplified.feeds.api.FeedEntry

/**
 * An image transformer that optionally adds a badge image to the loaded book cover.
 */

class BookCoverBadgePainter(
  val entry: FeedEntry.FeedEntryOPDS,
  val badges: BookCoverBadgeLookupType,
  val badgeOffset: BookCoverBadgeOffset
) : Transformation {

  override fun key(): String {
    return "org.nypl.simplified.books.covers.BookCoverBadgePainter"
  }

  override fun transform(source: Bitmap): Bitmap {
    val badge = this.badges.badgeForEntry(this.entry)
    if (badge == null) {
      return source
    }

    val workingBitmap = Bitmap.createBitmap(source)
    val result = workingBitmap.copy(source.config, true)
    val canvas = Canvas(result)

    val left = source.width - badge.width + badgeOffset.x*badge.offsetSize
    val right = source.width + badgeOffset.x*badge.offsetSize
    val top = source.height - badge.height + badgeOffset.y*badge.offsetSize
    val bottom = source.height + badgeOffset.y*badge.offsetSize
    val targetRect = Rect(left, top, right, bottom)

    drawBorder(canvas, badge, targetRect)

    val colorBackground = badge.backgroundColorRGBA()
    if (colorBackground != 0x00_00_00_00) {
      val backgroundPaint = Paint()
      backgroundPaint.color = colorBackground
      backgroundPaint.isAntiAlias = true
      canvas.drawRect(targetRect, backgroundPaint)
    }

    val imagePaint = Paint()
    imagePaint.isAntiAlias = true
    val sourceRect = Rect(0, 0, badge.bitmap.width, badge.bitmap.height)
    canvas.drawBitmap(badge.bitmap, sourceRect, targetRect, imagePaint)

    source.recycle()
    return result
  }

  private fun drawBorder(canvas: Canvas, badge: BookCoverBadge, badgeRect: Rect ) {
    val borderRect = Rect(badgeRect.left-badge.borderWidth,
      badgeRect.top - badge.borderWidth,
      badgeRect.right + badge.borderWidth,
      badgeRect.bottom + badge.borderWidth)
    val paint = Paint()
    paint.color = badge.borderColorRGBA()
    paint.isAntiAlias = false
    canvas.drawRect(borderRect, paint)
  }
}
