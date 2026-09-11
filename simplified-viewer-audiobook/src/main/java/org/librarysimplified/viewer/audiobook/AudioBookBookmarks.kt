package org.librarysimplified.viewer.audiobook

import org.joda.time.DateTimeZone
import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerBookmark
import org.librarysimplified.audiobook.api.PlayerBookmarkKind
import org.librarysimplified.audiobook.api.PlayerBookmarkMetadata
import org.librarysimplified.audiobook.api.PlayerPosition
import org.nypl.simplified.books.api.bookmark.Bookmark
import org.nypl.simplified.books.api.bookmark.BookmarkKind

/**
 * Functions to convert between e-kirjasto audiobook bookmarks and the audiobook 24.0.0
 * [PlayerBookmark] type.
 */

internal object AudioBookBookmarks {

  /**
   * Convert an e-kirjasto audiobook bookmark into a player bookmark.
   */

  fun toPlayerBookmark(
    bookmark: Bookmark.AudiobookBookmark
  ): PlayerBookmark {
    val kind =
      when (bookmark.kind) {
        BookmarkKind.BookmarkLastReadLocation -> PlayerBookmarkKind.LAST_READ
        BookmarkKind.BookmarkExplicit -> PlayerBookmarkKind.EXPLICIT
      }

    return PlayerBookmark(
      kind = kind,
      readingOrderID = bookmark.location.readingOrderID,
      offsetMilliseconds = bookmark.location.offsetMilliseconds,
      metadata = PlayerBookmarkMetadata(
        creationTime = bookmark.time,
        chapterTitle = "",
        totalRemainingBookTime = Duration.millis(bookmark.duration),
        chapterProgressEstimate = 0.0,
        bookProgressEstimate = 0.0
      )
    )
  }

  /**
   * Convert a player bookmark into an e-kirjasto audiobook bookmark.
   */

  fun fromPlayerBookmark(
    opdsId: String,
    deviceID: String,
    source: PlayerBookmark
  ): Bookmark.AudiobookBookmark {
    val kind =
      when (source.kind) {
        PlayerBookmarkKind.EXPLICIT -> BookmarkKind.BookmarkExplicit
        PlayerBookmarkKind.LAST_READ -> BookmarkKind.BookmarkLastReadLocation
      }

    return Bookmark.AudiobookBookmark(
      opdsId = opdsId,
      deviceID = deviceID,
      // Bookmark.AudiobookBookmark requires a UTC timestamp; the player's metadata creation time
      // is in the device's local zone.
      time = source.metadata.creationTime.toDateTime(DateTimeZone.UTC),
      kind = kind,
      uri = null,
      location = PlayerPosition(
        readingOrderID = source.readingOrderID,
        offsetMilliseconds = source.offsetMilliseconds
      ),
      duration = source.metadata.totalRemainingBookTime.millis
    )
  }
}
