package fi.kansalliskirjasto.ekirjasto.magazines

import java.io.Serializable

/**
 * Arguments used to browse or read magazines.
 */
sealed class MagazinesArguments : Serializable {
  /**
   * Token to be used to authenticate with the magazine service.
   */
  abstract val token: String?

  /**
   * Arguments used to browse or read magazines.
   */
  data class MagazinesArgumentsData(
    override val token: String?,
  ) : MagazinesArguments()
}
