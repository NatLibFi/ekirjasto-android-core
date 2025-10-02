package org.nypl.simplified.opds.core

import com.io7m.jfunctional.OptionType
import com.io7m.jfunctional.Unit
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

/**
 * The book is not available for borrowing but is available to place on hold.
 */

data class OPDSAvailabilityHoldable private constructor(
  val queue: OptionType<Int>,
  val copiesAvailable: OptionType<Int>,
  val copies: OptionType<Int>
) : OPDSAvailabilityType {

  val queueOrNull: Int?
    get() = this.queue.getOrNull()

  val copiesAvailableOrNull: Int?
    get() = this.copiesAvailable.getOrNull()

  val copiesOrNull: Int?
    get() = this.copies.getOrNull()

/**
 * Get availability end date (always none for Holdable)
 * @return end_date
 */
  override fun getEndDate(): OptionType<DateTime> {
    return this.endDate
  }

  override fun <A, E : Exception?> matchAvailability(
    m: OPDSAvailabilityMatcherType<A, E>
  ): A {
    return m.onHoldable(this)
  }

  override fun toString(): String {
    val fmt = ISODateTimeFormat.dateTime()
    val b = StringBuilder(256)
    b.append("[OPDSAvailabilityHoldable")
    b.append(" queue=")
    b.append(this.queue)
    b.append(" copiesAvailable=")
    b.append(this.copiesAvailable)
    b.append(" copies=")
    b.append(this.copies)
    b.append(" end_date=")
    this.endDate.map { e: DateTime? ->
      b.append(fmt.print(e))
      Unit.unit()
    }
    b.append("]")
    return b.toString()
  }

  companion object {
    private const val serialVersionUID = 1L

    /**
     * @param queue The length of queue for the book
     * @param copiesAvailable The number of copies available for the book
     * @param copies The number of copies for the book
     */

    @JvmStatic
    operator fun get(
      queue: OptionType<Int>,
      copiesAvailable: OptionType<Int>,
      copies: OptionType<Int>,
    ): OPDSAvailabilityHoldable {
      return OPDSAvailabilityHoldable(
        queue = queue,
        copiesAvailable = copiesAvailable,
        copies = copies,
      )
    }
  }  
}
