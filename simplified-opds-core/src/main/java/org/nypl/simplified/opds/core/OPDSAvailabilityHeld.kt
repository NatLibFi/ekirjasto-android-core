package org.nypl.simplified.opds.core

import com.io7m.jfunctional.OptionType
import com.io7m.jfunctional.Unit
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import java.net.URI

/**
 * The book is on hold.
 */

data class OPDSAvailabilityHeld private constructor(
  val startDate: OptionType<DateTime>,
  private val endDate: OptionType<DateTime>,
  val position: OptionType<Int>,
  val queue: OptionType<Int>,
  val copiesAvailable: OptionType<Int>,
  val copies: OptionType<Int>,

  /**
   * @return A URI for revoking the hold, if any
   */

  val revoke: OptionType<URI>

) : OPDSAvailabilityType {

  val startDateOrNull: DateTime?
    get() = this.startDate.getOrNull()

  val endDateOrNull: DateTime?
    get() = this.endDate.getOrNull()

  val revokeOrNull: URI?
    get() = this.revoke.getOrNull()

  val positionOrNull: Int?
    get() = this.position.getOrNull()

  val queueOrNull: Int?
    get() = this.queue.getOrNull()

  val copiesAvailableOrNull: Int?
    get() = this.copiesAvailable.getOrNull()

  val copiesOrNull: Int?
    get() = this.copies.getOrNull()

  /**
   * @return The date that the hold will become unavailable
   */

  override fun getEndDate(): OptionType<DateTime> {
    return this.endDate
  }

  override fun <A, E : Exception?> matchAvailability(
    m: OPDSAvailabilityMatcherType<A, E>
  ): A {
    return m.onHeld(this)
  }

  override fun toString(): String {
    val fmt = ISODateTimeFormat.dateTime()
    val b = StringBuilder(256)
    b.append("[OPDSAvailabilityHeld position=")
    b.append(this.position)
    b.append(" queue=")
    b.append(this.queue)
    b.append(" copiesAvailable=")
    b.append(this.copiesAvailable)
    b.append(" copies=")
    b.append(this.copies)
    b.append(" start_date=")
    this.startDate.map { e: DateTime? ->
      b.append(fmt.print(e))
      Unit.unit()
    }
    b.append(" end_date=")
    this.endDate.map { e: DateTime? ->
      b.append(fmt.print(e))
      Unit.unit()
    }
    b.append(" revoke=")
    b.append(this.revoke)
    b.append("]")
    return b.toString()
  }

  companion object {
    private const val serialVersionUID = 1L

    /**
     * @param startDate The start date (if known)
     * @param endDate The end date (if known)
     * @param position The queue position
     * @param queue The length of the queue for the book
     * @param copiesAvailable The number of available copies for the book
     * @param copies The number of copies for the book
     * @param revoke An optional revocation link for the hold
     * @return A value that states that a book is on hold
     */

    @JvmStatic
    operator fun get(
      startDate: OptionType<DateTime>,
      endDate: OptionType<DateTime>,
      position: OptionType<Int>,
      queue: OptionType<Int>,
      copiesAvailable: OptionType<Int>,
      copies: OptionType<Int>,
      revoke: OptionType<URI>
    ): OPDSAvailabilityHeld {
      return OPDSAvailabilityHeld(
        startDate = startDate,
        endDate = endDate,
        position = position,
        queue = queue,
        copiesAvailable = copiesAvailable,
        copies = copies,
        revoke = revoke
      )
    }
  }
}
