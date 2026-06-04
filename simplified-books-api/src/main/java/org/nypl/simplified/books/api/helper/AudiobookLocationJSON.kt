package org.nypl.simplified.books.api.helper

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID
import org.librarysimplified.audiobook.manifest.api.PlayerMillisecondsReadingOrderItem
import org.nypl.simplified.json.core.JSONParseException
import org.nypl.simplified.json.core.JSONParserUtilities
import org.nypl.simplified.json.core.JSONSerializerUtilities
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Functions to serialize audiobook locations to/from JSON.
 *
 * Note: As of the Readium 3.x / audiobook 24.0.0 migration, a player position is identified by a
 * reading-order item ID plus an offset in milliseconds within that item. The legacy
 * chapter/part/startOffset/currentOffset representation is gone. Legacy data that lacks the new
 * fields deserializes to the start of the book (empty reading-order ID, offset 0).
 */

object AudiobookLocationJSON {

  /**
   * Deserialize an audiobook player position from the given JSON node.
   *
   * @param node A JSON node
   * @return A parsed player position
   * @throws JSONParseException On parse errors
   */

  @Throws(JSONParseException::class)
  fun deserializeFromJSON(
    node: JsonNode
  ): PlayerPosition {
    val obj =
      JSONParserUtilities.checkObject(null, node)
    return PlayerPosition(
      readingOrderID = PlayerManifestReadingOrderID(
        text = JSONParserUtilities.getStringDefault(obj, "readingOrderID", "")
      ),
      offsetMilliseconds = PlayerMillisecondsReadingOrderItem(
        value = JSONParserUtilities.getIntegerDefault(obj, "offsetMilliseconds", 0).toLong()
      )
    )
  }

  /**
   * Serialize an audiobook player position to JSON.
   *
   * @param objectMapper A JSON object mapper
   * @param position The position of the audiobook
   * @return A serialized object
   */

  fun serializeToJSON(
    objectMapper: ObjectMapper,
    position: PlayerPosition
  ): ObjectNode {
    val root = objectMapper.createObjectNode()
    root.put("readingOrderID", position.readingOrderID.text)
    root.put("offsetMilliseconds", position.offsetMilliseconds.value)
    return root
  }

  /**
   * Serialize an audiobook player position to a JSON string.
   *
   * @param objectMapper A JSON object mapper
   * @param position The position in the audiobook
   * @return A JSON string
   * @throws IOException On serialization errors
   */

  @Throws(IOException::class)
  fun serializeToString(
    objectMapper: ObjectMapper,
    position: PlayerPosition
  ): String {
    val jo = serializeToJSON(objectMapper, position)
    val bao = ByteArrayOutputStream(1024)
    JSONSerializerUtilities.serialize(jo, bao)
    return bao.toString("UTF-8")
  }

  /**
   * Deserialize an audiobook player position from the given string.
   *
   * @param objectMapper A JSON object mapper
   * @param text The text to map
   * @return A parsed player position
   * @throws IOException On I/O or parser errors
   */

  @Throws(IOException::class)
  fun deserializeFromString(
    objectMapper: ObjectMapper,
    text: String
  ): PlayerPosition {
    val node = objectMapper.readTree(text)
    return deserializeFromJSON(
      node = JSONParserUtilities.checkObject(null, node)
    )
  }
}
