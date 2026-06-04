package org.librarysimplified.viewer.audiobook

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import java.net.URI
import java.util.UUID

/**
 * Parameters for the audio book player.
 *
 * As of the audiobook 24.0.0 migration, manifest fulfillment/parsing/license-checking and engine
 * selection are owned by [org.librarysimplified.audiobook.views.PlayerModel]. These parameters
 * therefore only carry the identity of the book being played plus the DRM information needed to
 * derive the player credentials.
 */

class AudioBookPlayerParameters(

  /**
   * The account to which the book belongs.
   */

  val accountID: AccountID,

  /**
   * The account provider to which the book belongs.
   */

  val accountProviderID: URI,

  /**
   * The book ID.
   */

  val bookID: BookID,

  /**
   * The DRM information for the book.
   */

  val drmInfo: BookDRMInformation,

  /**
   * The OPDS entry for the book.
   */

  val opdsEntry: OPDSAcquisitionFeedEntry,

  /**
   * The unique ID of the player instance used for this book.
   */

  val playerID: UUID
)
