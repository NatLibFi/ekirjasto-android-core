package org.nypl.simplified.books.covers

data class BookCoverBadgeOffset (
  val x: Int,
  val y: Int
){
  /** Determine static offsets in DP, which is then scaled to pixels in the cover image **/
  companion object {
    @JvmStatic
    val OFFSET_DEFAULT: BookCoverBadgeOffset = BookCoverBadgeOffset(0,0)
    @JvmStatic
    val OFFSET_HORIZONTAL: BookCoverBadgeOffset = BookCoverBadgeOffset(0,0)
    @JvmStatic
    val OFFSET_VERTICAL: BookCoverBadgeOffset = BookCoverBadgeOffset(0,0)
  }
}
