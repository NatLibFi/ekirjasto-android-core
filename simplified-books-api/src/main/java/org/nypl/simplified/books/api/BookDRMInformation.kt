package org.nypl.simplified.books.api

import org.librarysimplified.audiobook.api.PlayerBookCredentialsLCP
import org.librarysimplified.audiobook.api.PlayerBookCredentialsNone
import org.librarysimplified.audiobook.api.PlayerBookCredentialsType
import org.nypl.drm.core.AdobeAdeptLoan
import java.io.File
import java.io.Serializable

/**
 * The `BookDRMInformation` class represents an immutable snapshot of the current DRM
 * information associated with a book.
 */

sealed class BookDRMInformation : Serializable {

  /**
   * The kind of DRM
   */

  abstract val kind: BookDRMKind

  /**
   * The Adobe ACS information associated with a book.
   */

  data class ACS(

    /**
     * The ACSM file. This is only present if an attempt has been made to fulfill the book.
     */

    val acsmFile: File?,

    /**
     * The rights information. This is only present if the book has been fulfilled.
     */

    val rights: Pair<File, AdobeAdeptLoan>?
  ) : BookDRMInformation() {
    override val kind: BookDRMKind = BookDRMKind.ACS
  }

  /**
   * The LCP information associated with a book.
   */

  data class LCP(

    /**
     * The hashed LCP passphrase for the book.
     */

    val hashedPassphrase: String?
  ) : BookDRMInformation() {
    override val kind: BookDRMKind = BookDRMKind.LCP
  }

  /**
   * The book either has no DRM, or uses some kind of external DRM system that the book database
   * doesn't know about (such as proprietary AudioBook DRM).
   */

  object None : BookDRMInformation() {
    override val kind: BookDRMKind = BookDRMKind.NONE
  }
}

/**
 * Map the DRM information of a book onto the player credentials required by the audiobook engine
 * (audiobook 24.0.0+). LCP audiobooks are decrypted using the book's hashed passphrase; everything
 * else needs no player-level credentials.
 */

fun BookDRMInformation.playerCredentials(): PlayerBookCredentialsType {
  return when (this) {
    is BookDRMInformation.LCP ->
      PlayerBookCredentialsLCP(passphrase = this.hashedPassphrase ?: "")
    is BookDRMInformation.ACS,
    BookDRMInformation.None ->
      PlayerBookCredentialsNone
  }
}
