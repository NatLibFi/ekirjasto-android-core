package org.nypl.simplified.ui.accounts.ekirjasto

/**
 * Data class for dependents.
 */
data class Dependent(
  var locale: String = "fi",
  val firstName: String,
  val lastName: String,
  val govId: String,
  var email: String = "",
  val role: String = "customer"
)
