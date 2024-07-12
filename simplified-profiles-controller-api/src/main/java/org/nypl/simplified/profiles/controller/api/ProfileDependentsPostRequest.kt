package org.nypl.simplified.profiles.controller.api

sealed class ProfileDependentsPostRequest {
  data class Ekirjasto(
    val ekirjastoToken: String,
    val dependent: String //Change to actual dependent at some point
  ) : ProfileDependentsPostRequest()
}

