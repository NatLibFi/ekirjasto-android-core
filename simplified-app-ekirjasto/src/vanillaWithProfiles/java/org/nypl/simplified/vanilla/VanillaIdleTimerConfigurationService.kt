package fi.kansalliskirjasto.ekirjasto

import org.nypl.simplified.profiles.api.idle_timer.ProfileIdleTimerConfigurationServiceType

class VanillaIdleTimerConfigurationService : ProfileIdleTimerConfigurationServiceType {

  override val warningWhenSecondsRemaining: Int
    get() = 60

  override val logOutAfterSeconds: Int
    get() = 10 * 60
}
