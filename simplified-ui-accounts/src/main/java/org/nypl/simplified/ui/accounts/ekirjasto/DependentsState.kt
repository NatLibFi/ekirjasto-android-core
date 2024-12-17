package org.nypl.simplified.ui.accounts.ekirjasto


/**
 * The state of the dependents.
 */
sealed class DependentsState {
  /**
   * Ekirjasto token. Can be either the actual token or a message used to trigger another action
   * if lookup of the token went wrong.
   */
  abstract val ekirjastoToken: String?

  /**
   * Something went wrong in getting the ekirjasto token. Token should be set to null.
   */
  data class EkirjastoTokenLoadFailed(
    override val ekirjastoToken: String?,
  ) : DependentsState()

  /**
   * A part of the dependents lookup process is still going on.
   */
  data class DependentsLoading(
    override val ekirjastoToken: String?,
  ) : DependentsState()

  /**
   * The lookup for ekirjastoToken was successful, and is set as the value. This should be set to
   * the observable variable.
   */
  data class DependentsTokenFound(
    override val ekirjastoToken: String?,
  ) : DependentsState()

  /**
   * Server returned an unexpected error status to some HTTP call.
   */
  data class DependentsLookupError(
    override val ekirjastoToken: String?,
  ) : DependentsState()
}
