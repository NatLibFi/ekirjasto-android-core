package org.nypl.simplified.tests.bookmarks

import com.fasterxml.jackson.databind.ObjectMapper
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.ISODateTimeFormat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.nypl.simplified.books.api.bookmark.BookmarkJSON
import org.nypl.simplified.books.api.bookmark.BookmarkKind

/*
 * As of the Readium 3.x / audiobook 24.0.0 migration, an audiobook player position is identified
 * by a reading-order item ID plus an offset in milliseconds within that item. The legacy
 * chapter/part/startOffset/currentOffset representation is gone, and the location fields are now
 * serialized flat onto the bookmark object (readingOrderID + offsetMilliseconds) rather than nested
 * under a "location" key.
 */

class AudiobookBookmarkJSONTest {

  private lateinit var objectMapper: ObjectMapper
  private lateinit var formatter: DateTimeFormatter

  @BeforeEach
  fun testSetup() {
    this.objectMapper = ObjectMapper()
    this.formatter = ISODateTimeFormat.dateTime().withZoneUTC()
  }

  @AfterEach
  fun tearDown() {
    DateTimeZone.setDefault(DateTimeZone.getDefault())
  }

  @Test
  fun testDeserializeJSON() {
    val bookmark = BookmarkJSON.deserializeAudiobookBookmarkFromString(
      objectMapper = this.objectMapper,
      kind = BookmarkKind.BookmarkLastReadLocation,
      serialized = """
        {
          "readingOrderID" : "reading-order-1",
          "offsetMilliseconds" : 100000,
          "opdsId" : "urn:isbn:9781683609438",
          "time" : "2022-06-27T14:51:46.238",
          "deviceID" : "null"
        }
      """
    )

    assertEquals("reading-order-1", bookmark.location.readingOrderID.text)
    assertEquals(100000L, bookmark.location.offsetMilliseconds.value)
    assertEquals("urn:isbn:9781683609438", bookmark.opdsId)

    val serializedText =
      BookmarkJSON.serializeAudiobookBookmarkToString(this.objectMapper, bookmark)
    val serialized =
      BookmarkJSON.deserializeAudiobookBookmarkFromString(
        objectMapper = this.objectMapper,
        kind = bookmark.kind,
        serialized = serializedText
      )
    assertEquals(bookmark, serialized)
  }

  /*
   * Legacy bookmarks that lack the new reading-order/offset fields deserialize to the start of the
   * book (empty reading-order ID, offset 0) rather than failing.
   */

  @Test
  fun testDeserializeLegacyFallsBackToStart() {
    val bookmark = BookmarkJSON.deserializeAudiobookBookmarkFromString(
      objectMapper = this.objectMapper,
      kind = BookmarkKind.BookmarkLastReadLocation,
      serialized = """
        {
          "@version" : 2,
          "opdsId" : "urn:isbn:9781683609438",
          "location" : {
            "chapter" : 1,
            "part" : 2,
            "title" : "Is That You, Walt Whitman?",
            "time" : 100000
          },
          "time" : "2022-06-27T14:51:46.238",
          "chapterTitle" : "Is That You, Walt Whitman?",
          "deviceID" : "null"
        }
      """
    )

    assertEquals("", bookmark.location.readingOrderID.text)
    assertEquals(0L, bookmark.location.offsetMilliseconds.value)
  }
}
