package org.librarysimplified.documents

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService

interface DocumentStoreType {

  /**
   * @return The application privacy policy, if any.
   */

  val privacyPolicy: DocumentType?

  /**
   * @return The application feedback form, if any.
   */

  val feedback: DocumentType?

  /**
   * @return The application accessibility statement, if any.
   */

  val accessibilityStatement: DocumentType?

  /**
   * @return The application acknowledgements, if any.
   */

  val about: DocumentType?

  /**
   * @return The application acknowledgements, if any.
   */

  val acknowledgements: DocumentType?

  /**
   * @return The EULA, if any
   */

  val eula: EULAType?

  /**
   * @return The application licenses, if any.
   */

  val licenses: DocumentType?

  /**
   * @return The application Instruction in finnish, if any.
   */
  val instructionsFI: DocumentType?

  /**
   * @return The application Instruction in swedish, if any.
   */
  val instructionsSV: DocumentType?

  /**
   * @return The application Instruction in english, if any.
   */
  val instructionsEN: DocumentType?

  /**
   * @return The application FAQ, if any.
   */

  val faq: DocumentType?

  /**
   * Run updates for all of the documents.
   */

  fun update(executor: ListeningExecutorService): ListenableFuture<*>
}
