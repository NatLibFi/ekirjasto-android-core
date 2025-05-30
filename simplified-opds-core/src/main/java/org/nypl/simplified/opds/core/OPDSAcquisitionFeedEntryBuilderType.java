package org.nypl.simplified.opds.core;

import com.io7m.jfunctional.OptionType;

import org.joda.time.DateTime;
import org.nypl.simplified.parser.api.ParseError;

import java.net.URI;
import java.util.List;

/**
 * The type of mutable builders for {@link OPDSAcquisitionFeedEntry} values.
 */

public interface OPDSAcquisitionFeedEntryBuilderType {
  /**
   * Add a parse error.
   *
   * @param error The parse error
   */

  OPDSAcquisitionFeedEntryBuilderType addParseError(
    ParseError error);

  /**
   * Add an acquisition.
   *
   * @param a The acquisition
   */

  OPDSAcquisitionFeedEntryBuilderType addAcquisition(
    OPDSAcquisition a);

  /**
   * Set the author.
   *
   * @param name The author
   */

  OPDSAcquisitionFeedEntryBuilderType addAuthor(
    final String name);

  /**
   * Add a category.
   *
   * @param c The category
   */

  OPDSAcquisitionFeedEntryBuilderType addCategory(
    final OPDSCategory c);

  /**
   * Add a group.
   *
   * @param uri The group URI
   * @param b   The group name
   */

  OPDSAcquisitionFeedEntryBuilderType addGroup(
    final URI uri,
    final String b);

  /**
   * @return An entry based on all of the given values
   */

  OPDSAcquisitionFeedEntry build();

  /**
   * @return A list of the current acquisitions
   */

  List<OPDSAcquisition> getAcquisitions();

  /**
   * Set the availability.
   *
   * @param a The availability
   */

  OPDSAcquisitionFeedEntryBuilderType setAvailability(
    OPDSAvailabilityType a);

  /**
   * Set the cover.
   *
   * @param uri The cover URI
   */

  OPDSAcquisitionFeedEntryBuilderType setCoverOption(
    OptionType<URI> uri);

  /**
   * Add a preview.
   *
   * @param previewAcquisition The preview acquisition
   */

  OPDSAcquisitionFeedEntryBuilderType addPreviewAcquisition(
    OPDSPreviewAcquisition previewAcquisition);

  /**
   * @param uri The annotations URI
   */

  OPDSAcquisitionFeedEntryBuilderType setAnnotationsOption(
    OptionType<URI> uri);

  /**
   * Set selected
   *
   * @param selected Selected DateTime or not
   */
  OPDSAcquisitionFeedEntryBuilderType setSelectedOption(
    OptionType<DateTime> selected);

  /**
   * @param uri The alternate URI
   */

  OPDSAcquisitionFeedEntryBuilderType setAlternateOption(
    OptionType<URI> uri);

  /**
   * @param uri The analytics URI
   */

  OPDSAcquisitionFeedEntryBuilderType setAnalyticsOption(
    OptionType<URI> uri);

  /**
   * Set the report issues URI.
   *
   * @param uri The report issues URI
   */

  OPDSAcquisitionFeedEntryBuilderType setIssuesOption(
    OptionType<URI> uri);

  /**
   * @param uri The Related feed URI
   */

  OPDSAcquisitionFeedEntryBuilderType setRelatedOption(
    OptionType<URI> uri);

  /**
   * Set the publication date.
   *
   * @param pub The publication date
   */

  OPDSAcquisitionFeedEntryBuilderType setPublishedOption(
    OptionType<DateTime> pub);

  /**
   * Set the publisher.
   *
   * @param pub The publisher
   */

  OPDSAcquisitionFeedEntryBuilderType setPublisherOption(
    OptionType<String> pub);

  /**
   * Set the distribution.
   *
   * @param dist The distribution
   */

  OPDSAcquisitionFeedEntryBuilderType setDistribution(
    String dist);

  /**
   * Set the summary.
   *
   * @param text The summary
   */

  OPDSAcquisitionFeedEntryBuilderType setSummaryOption(
    OptionType<String> text);

  /**
   * Set the narrator.
   *
   * @param name The narrator's name
   */

  OPDSAcquisitionFeedEntryBuilderType addNarrator(
    final String name);

  /**
   * Set the translator.
   *
   * @param name The translator's name
   */
  OPDSAcquisitionFeedEntryBuilderType addTranslator(
    final String name);

  /**
   * Set the illustrator.
   *
   * @param name The illustrators's name
   */
  OPDSAcquisitionFeedEntryBuilderType addIllustrator(
    final String name);

  /**
   * Set the language
   *
   * @param language
   * @return
   */
  OPDSAcquisitionFeedEntryBuilderType setLanguageOption(
    OptionType<String> language);

  /**
   * Set the thumbnail.
   *
   * @param uri The thumbnail
   */

  OPDSAcquisitionFeedEntryBuilderType setThumbnailOption(
    OptionType<URI> uri);

  /**
   * Set the time tracking uri.
   *
   * @param uri The time tracking uri
   */

  OPDSAcquisitionFeedEntryBuilderType setTimeTrackingUriOption(
    OptionType<URI> uri);

  /**
   * @param licensor The Licensor
   */

  OPDSAcquisitionFeedEntryBuilderType setLicensorOption(
    OptionType<DRMLicensor> licensor);

  /**
   * @param duration The duration in seconds
   */
  OPDSAcquisitionFeedEntryBuilderType setDurationOption(
    OptionType<Double> duration);

}
