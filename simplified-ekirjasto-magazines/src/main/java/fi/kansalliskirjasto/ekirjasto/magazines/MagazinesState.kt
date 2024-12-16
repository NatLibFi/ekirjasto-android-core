package fi.kansalliskirjasto.ekirjasto.magazines


/**
 * The state of the magazines browser.
 */
sealed class MagazinesState {
  abstract val arguments: MagazinesArguments

  data class MagazinesLoading(
    override val arguments: MagazinesArguments,
  ) : MagazinesState()

  data class MagazinesLoadFailed(
    override val arguments: MagazinesArguments,
    val login: Boolean = false
  ) : MagazinesState()

  data class MagazinesBrowsing(
    override val arguments: MagazinesArguments,
  ) : MagazinesState()

  data class MagazinesReading(
    override val arguments: MagazinesArguments,
  ) : MagazinesState()
}
