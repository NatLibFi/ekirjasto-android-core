package org.nypl.simplified.profiles.controller.api

sealed class ProfileDependentsLookupRequest {
  data class Ekirjasto(
    val patronInfo: String,
    val ekirjastoToken: String,
  ) : ProfileDependentsLookupRequest()
}
