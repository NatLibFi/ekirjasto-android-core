package org.nypl.simplified.opds.core

import com.io7m.jfunctional.Option
import com.io7m.jfunctional.OptionType
import com.io7m.jfunctional.Unit
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

/**
 * The book is available for borrowing.
 */

data class OPDSAvailabilityLoanable private constructor(
  val copiesAvailable: OptionType<Int>,
  val copies: OptionType<Int>
) : OPDSAvailabilityType {

  val copiesAvailableOrNull: Int?
    get() = this.copiesAvailable.getOrNull()

  val copiesOrNull: Int?
    get() = this.copies.getOrNull()

  /**
   * Get availability end date (always none for Loanable)
   * @return end_date
   */
  override fun getEndDate(): OptionType<DateTime> {
    return this.endDate
  }

  override fun <A, E : Exception?> matchAvailability(
    m: OPDSAvailabilityMatcherType<A, E>
  ): A {
    return m.onLoanable(this)
  }

  override fun toString(): String {
    val fmt = ISODateTimeFormat.dateTime()
    val b = StringBuilder(256)
    b.append("[OPDSAvailabilityLoanable")
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
     * @param copiesAvailable The number of copies currently available
     * @param copies The number of copies for the book
     */

    @JvmStatic
    operator fun get(
      copiesAvailable: OptionType<Int>,
      copies: OptionType<Int>,
    ): OPDSAvailabilityLoanable {
      return OPDSAvailabilityLoanable(
        copiesAvailable = copiesAvailable,
        copies = copies,
      )
    }

    @JvmStatic
    fun get(): OPDSAvailabilityType {
      return OPDSAvailabilityLoanable (
        copiesAvailable = Option.none(),
        copies = Option.none()
      )
    }
  }
}
