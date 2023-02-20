package org.nypl.simplified.opds.core;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * <p>The type of serializers that produce simple JSON in a private format from
 * OPDS feeds. </p>
 */

public interface OPDSJSONSerializerType {
  /**
   * Serialize the given feed to JSON.
   *
   * @param e The feed
   * @return JSON
   * @throws OPDSSerializationException On serialization errors
   */

  ObjectNode serializeFeed(
    OPDSAcquisitionFeed e)
    throws OPDSSerializationException;

  /**
   * Serialize the given feed entry to JSON.
   *
   * @param e The feed entry
   * @return JSON
   * @throws OPDSSerializationException On serialization errors
   */

  ObjectNode serializeFeedEntry(
    OPDSAcquisitionFeedEntry e)
    throws OPDSSerializationException;

  /**
   * Serialize the given availability type to JSON.
   *
   * @param a The availability type
   * @return JSON
   */

  ObjectNode serializeAvailability(
    OPDSAvailabilityType a);

  /**
   * Serialize the given acquisition to JSON.
   *
   * @param a The acquisition
   * @return JSON
   */

  ObjectNode serializeAcquisition(
    OPDSAcquisition a) throws OPDSSerializationException;

  /**
   * Serialize the given preview acquisition to JSON.
   *
   * @param a The preview acquisition
   * @return JSON
   */

  ObjectNode serializePreviewAcquisition(
    OPDSPreviewAcquisition a) throws OPDSSerializationException;

  /**
   * Serialize the given category to JSON.
   *
   * @param c The category
   * @return JSON
   */

  ObjectNode serializeCategory(
    OPDSCategory c);

  /**
   * @param l the licensor
   * @return JSON
   */

  ObjectNode serializeLicensor(
    DRMLicensor l);

  /**
   * Serialize the given list of indirect acquisitions.
   *
   * @param indirects The indirect acquisitions
   * @return JSON
   * @throws OPDSSerializationException On errors
   */

  ArrayNode serializeIndirectAcquisitions(
    List<OPDSIndirectAcquisition> indirects)
    throws OPDSSerializationException;

  /**
   * Serialize the given indirect acquisition.
   *
   * @param indirect The indirect acquisition
   * @return JSON
   * @throws OPDSSerializationException On errors
   */

  ObjectNode serializeIndirectAcquisition(
    OPDSIndirectAcquisition indirect)
    throws OPDSSerializationException;

  /**
   * Serialize the given JSON to the given output stream.
   *
   * @param d  The JSON
   * @param os The output stream
   * @throws IOException On I/O errors
   */

  void serializeToStream(
    ObjectNode d,
    OutputStream os)
    throws IOException;
}
