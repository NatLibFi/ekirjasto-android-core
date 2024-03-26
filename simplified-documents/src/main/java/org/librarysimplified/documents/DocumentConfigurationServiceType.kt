package org.librarysimplified.documents

/**
 * The document configuration service.
 */

interface DocumentConfigurationServiceType {

  /**
   * @return The application privacy policy, if any.
   */

  val privacyPolicy: DocumentConfiguration?

  /**
   * @return The application feedback form, if any.
   */
  val feedback: DocumentConfiguration?

  /**
   * @return The accessibility statement, if any.
   */
  val accessibilityStatement: DocumentConfiguration?

  /**
   * @return The application acknowledgements, if any.
   */

  val about: DocumentConfiguration?

  /**
   * @return The application acknowledgements, if any.
   */

  val acknowledgements: DocumentConfiguration?

  /**
   * @return The EULA, if any
   */

  val eula: DocumentConfiguration?

  /**
   * @return The application licenses, if any.
   */

  val licenses: DocumentConfiguration?

  /**
   * @return The application FAQ, if any.
   */

  val faq: DocumentConfiguration?
}
