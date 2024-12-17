package org.nypl.simplified.ui.accounts.ekirjasto

import com.fasterxml.jackson.databind.JsonNode


/**
 * The result of an asynchronous HTTP request related to dependents lookup.
 */
sealed class DependentsHttpResult {
  /**
   * Successful HTTP result for the ekirjastoToken from circulation
   */
  data class DependentsHttpTokenSuccess(
    val ekirjastoToken: String
  ) : DependentsHttpResult()

  /**
   * Error HTTP result for the ekirjastoToken from circulation
   */
  data class DependentsHttpTokenError(
    val message: String?
  ) : DependentsHttpResult()

  /**
   * Failed HTTP result for the ekirjastoToken
   */
  data class DependentsHttpTokenFailure(
    val message: String?
  ) : DependentsHttpResult()

  /**
   * Successful HTTP result for the list of dependents from the API
   */
  data class DependentsHttpDependentLookupSuccess(
    val dependents: MutableList<Dependent>
  ) : DependentsHttpResult()

  /**
   * Error HTTP result for dependents lookup from the API
   */
  data class DependentsHttpDependentLookupError(
    val message: String?
  ) : DependentsHttpResult()

  /**
   * Failed HTTP result for dependents
   */
  data class DependentsHttpDependentLookupFailure(
    val message: String?
  ) : DependentsHttpResult()
}
