package org.nypl.simplified.accessibility

import android.content.res.Resources
import org.librarysimplified.accessibility.R

/**
 * The default implementation of the [AccessibilityStringsType].
 */

class AccessibilityStrings(
  private val resources: Resources
) : AccessibilityStringsType {
  override fun bookHasDownloaded(title: String): String =
    this.resources.getString(R.string.bookHasDownloaded, title)

  override fun bookIsDownloading(title: String): String =
    this.resources.getString(R.string.bookIsDownloading, title)

  override fun bookIsOnHold(title: String): String =
    this.resources.getString(R.string.bookIsOnHold, title)

  override fun bookReturned(title: String): String =
    this.resources.getString(R.string.bookReturned, title)

  override fun bookFailedReturn(title: String): String =
    this.resources.getString(R.string.bookFailedReturn, title)

  override fun bookFailedLoan(title: String): String =
    this.resources.getString(R.string.bookFailedLoan, title)

  override fun bookFailedDownload(title: String): String =
    this.resources.getString(R.string.bookFailedDownload, title)

  override fun bookLoanLimitReached(): String =
    this.resources.getString(R.string.reachedLoanLimit)

  override fun bookSelected(title: String): String =
    this.resources.getString(R.string.bookSelected, title)

  override fun bookUnselected(title: String): String =
    this.resources.getString(R.string.bookUnselected, title)
}
