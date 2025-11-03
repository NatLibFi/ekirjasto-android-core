package org.nypl.simplified.opds.core

import java.io.Serializable

data class OPDSAccessibility (
  val waysOfReading: List<String>?, // CHECK Author for example
  val conformsTo: List<String>?,
): Serializable {

  companion object {
    private const val serialVersionUID = 1L
  }
}
