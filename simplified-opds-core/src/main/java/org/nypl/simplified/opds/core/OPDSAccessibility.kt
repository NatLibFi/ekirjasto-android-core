package org.nypl.simplified.opds.core

import java.io.Serializable

data class OPDSAccessibility (
  val waysOfReading: List<String>?,
  val conformsTo: List<String>?,
): Serializable {

  companion object {
    private const val serialVersionUID = 1L
  }
}
