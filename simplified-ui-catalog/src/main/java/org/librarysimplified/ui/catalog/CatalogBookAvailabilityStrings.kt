package org.librarysimplified.ui.catalog

import android.content.res.Resources
import com.io7m.jfunctional.Option
import com.io7m.jfunctional.OptionType
import com.io7m.jfunctional.Some
import org.joda.time.DateTime
import org.joda.time.Hours
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.opds.core.getOrNull
import java.util.concurrent.TimeUnit

/**
 * Functions that map book availability values to human-readable strings.
 */

object CatalogBookAvailabilityStrings {

  /**
   * Produce a human-readable string for the given book status.
   *
   * @param resources The application resources
   * @param status The status value
   *
   * @return A descriptive string
   */

  fun statusString(
    resources: Resources,
    status: BookStatus
  ): String {
    return when (status) {
      is BookStatus.Held.HeldInQueue ->
        onHeld(resources, Option.of(status.copiesTotal), Option.of(status.queuePosition))
      is BookStatus.Held.HeldReady ->
        onLoanable(resources)
      is BookStatus.Holdable ->
        onHoldable(resources)
      is BookStatus.Loanable ->
        onLoanable(resources)
      is BookStatus.Loaned.LoanedNotDownloaded ->
        if (status.isOpenAccess) {
          onLoanable(resources)
        } else {
          onLoaned(resources, Option.of(status.loanExpiryDate))
        }
      is BookStatus.Loaned.LoanedDownloaded ->
        onLoaned(resources, Option.of(status.loanExpiryDate))
      is BookStatus.RequestingLoan ->
        ""
      is BookStatus.Revoked ->
        onRevoked(resources)
      is BookStatus.FailedLoan ->
        ""
      is BookStatus.FailedRevoke ->
        ""
      is BookStatus.FailedDownload ->
        ""
      is BookStatus.RequestingRevoke ->
        ""
      is BookStatus.RequestingDownload ->
        ""
      is BookStatus.Downloading ->
        ""
      is BookStatus.DownloadWaitingForExternalAuthentication ->
        ""
      is BookStatus.DownloadExternalAuthenticationInProgress ->
        ""
      is BookStatus.ReachedLoanLimit ->
        ""
      is BookStatus.Selected ->
        ""
      is BookStatus.Unselected ->
        ""
    }
  }

  private fun onRevoked(resources: Resources): String {
    return resources.getString(R.string.catalogBookAvailabilityRevoked)
  }

  /**
   * Returns a short form of the reserved book's hold information.
   * Function is public so it can be called past the bookStatus check,
   * but the book needs to be in status BookStatus.Held.HeldInQueue.
   */
  fun onHeldShort(
    resources: Resources,
    status: BookStatus.Held.HeldInQueue
  ): String {
    val queuePositionOpt : OptionType<Int> = Option.of(status.queuePosition)

    /**
   * If there is a queue position, attempt to show it.
   */

    if (queuePositionOpt is Some<Int>) {
      return resources.getString(R.string.catalogBookAvailabilityHeldQueueShort, queuePositionOpt.get())
    }

    /**
     * Otherwise, show an indefinite hold.
     */

    return resources.getString(R.string.catalogBookAvailabilityHeldIndefiniteShort)
  }
  private fun onOpenAccess(resources: Resources): String {
    return resources.getString(R.string.catalogBookAvailabilityOpenAccess)
  }

  private fun onLoanable(resources: Resources): String {
    return resources.getString(R.string.catalogBookAvailabilityLoanable)
  }

  private fun onHoldable(resources: Resources): String {
    return resources.getString(R.string.catalogBookAvailabilityHoldable)
  }

  private fun onLoaned(
    resources: Resources,
    expiryOpt: OptionType<DateTime>
  ): String {
    /*
     * If there is an expiry time, display it.
     */

    if (expiryOpt is Some<DateTime>) {
      val expiry = expiryOpt.get()
      val now = DateTime.now()
      return resources.getString(
        R.string.catalogBookAvailabilityLoanedTimed, this.intervalString(resources, now, expiry)
      )
    }

    /*
     * Otherwise, show an indefinite loan.
     */

    return resources.getString(R.string.catalogBookAvailabilityLoanedIndefinite)
  }

  private fun onReserved(
    resources: Resources,
    expiryOpt: OptionType<DateTime>
  ): String {
    /*
     * If there is an expiry time, display it.
     */

    if (expiryOpt is Some<DateTime>) {
      val expiry = expiryOpt.get()
      val now = DateTime.now()
      return resources.getString(
        R.string.catalogBookAvailabilityReservedTimed,
        this.intervalString(resources, now, expiry)
      )
    }

    /*
     * Otherwise, show an indefinite reservation.
     */

    return resources.getString(R.string.catalogBookAvailabilityReservedIndefinite)
  }

  private fun onHeld(
    resources: Resources,
    copiesTotalOpt: OptionType<Int>,
    queuePositionOpt: OptionType<Int>
  ): String {

    /**
     * If there is a queue position, attempt to show it.
     */

    if (queuePositionOpt is Some<Int>) {
      if (copiesTotalOpt is Some<Int>) {
        return resources.getString(R.string.catalogBookAvailabilityHeldQueueWithCopies, queuePositionOpt.get(), copiesTotalOpt.getOrNull())
      }
      return resources.getString(R.string.catalogBookAvailabilityHeldQueue, queuePositionOpt.get())
    }

    /**
     * Otherwise, show an indefinite hold.
     */

    return resources.getString(R.string.catalogBookAvailabilityHeldIndefinite)
  }

  /**
   *
   * Construct a time interval string based on the given times. The string
   * will be a localized form of:
   *
   *  * `less than an hour` iff the period is under one hour
   *  * `n hours` iff the period is under one day
   *  * `n days` otherwise
   *
   * @param resources The application resources
   * @param lower The lower bound of the time period
   * @param upper The upper bound of the time period
   *
   * @return A time interval string
   */

  fun intervalString(
    resources: Resources,
    lower: DateTime,
    upper: DateTime
  ): String {
    val hours = this.calendarHoursBetween(lower, upper)

    if (hours < 1) {
      return resources.getString(R.string.catalogBookIntervalSubHour)
    }
    if (hours < 24) {
      val base = resources.getString(R.string.catalogBookIntervalHours)
      return String.format("%d %s", hours, base)
    }

    val base = resources.getString(R.string.catalogBookIntervalDays)
    return String.format("%d %s", TimeUnit.HOURS.toDays(hours), base)
  }
  /**
   * Construct a short time interval string like "3 d", with units up to days, that will be used
   * to show the user the duration of a book's hold.
   *
   * @param resources The application resources
   * @param lower The lower bound of the time period
   * @param upper The upper bound of the time period
   *
   * @return A time interval string
   */

  fun intervalStringHoldDuration(
    resources: Resources,
    lower: DateTime,
    upper: DateTime
  ): String {
    val hours = this.calendarHoursBetween(lower, upper)
    val days = TimeUnit.HOURS.toDays(hours)

    val unit: String
    val value: Long

    when {
      // Switch to days after 48 hours.
      days >= 2 -> {
        unit = resources.getString(R.string.catalogBookIntervalDaysShort)
        value = days
      }

      // Use hours.
      hours > 0 -> {
        unit = resources.getString(R.string.catalogBookIntervalHoursShort)
        value = hours
      }

      else -> {
        return ""
      }
    }

    return String.format("%d %s", value, unit)
  }

  /**
   * Construct a short time interval string like "3 w", with units up to a year, that will be used
   * to show the user the duration of his book's loan.
   *
   * @param resources The application resources
   * @param lower The lower bound of the time period
   * @param upper The upper bound of the time period
   *
   * @return A time interval string
   */

  fun intervalStringLoanDuration(
    resources: Resources,
    lower: DateTime,
    upper: DateTime
  ): String {
    val hours = this.calendarHoursBetween(lower, upper)
    val days = TimeUnit.HOURS.toDays(hours)
    val weeks = days / 7
    val months = days / 30
    val years = days / 365

    val unit: String
    val value: Long

    when {
      // Switch to years after ~48 months.
      years >= 4 -> {
        unit = resources.getString(R.string.catalogBookIntervalYearsShort)
        value = years
      }

      // Switch to months after ~16 weeks.
      months >= 4 -> {
        unit = resources.getString(R.string.catalogBookIntervalMonthsShort)
        value = months
      }

      // Switch to weeks after 28 days.
      weeks >= 4 -> {
        unit = resources.getString(R.string.catalogBookIntervalWeeksShort)
        value = weeks
      }

      // Switch to days after 48 hours.
      days >= 2 -> {
        unit = resources.getString(R.string.catalogBookIntervalDaysShort)
        value = days
      }

      // Use hours.
      hours > 0 -> {
        unit = resources.getString(R.string.catalogBookIntervalHoursShort)
        value = hours
      }

      else -> {
        return ""
      }
    }

    return String.format("%d %s", value, unit)
  }

  private fun calendarHoursBetween(
    start: DateTime,
    end: DateTime
  ): Long {
    return Hours.hoursBetween(start, end).hours.toLong()
  }
}
