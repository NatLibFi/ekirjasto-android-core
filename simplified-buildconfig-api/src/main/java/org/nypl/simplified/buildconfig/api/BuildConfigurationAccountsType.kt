package org.nypl.simplified.buildconfig.api

/**
 * Configuration values related to accounts.
 */

interface BuildConfigurationAccountsType {

  /**
   * The base URI for the library registry.
   */

  val libraryRegistry: BuildConfigurationAccountsRegistryURIs

  /**
   * If set to `true`, then users are allowed access to the accounts panel and
   * can add/remove accounts. If set to `false`, the accounts setting item is
   * removed.
   */

  val allowAccountsAccess: Boolean

  /**
   * If set to `true`, then users are allowed access to the accounts registry and
   * can therefore create new accounts from that registry. This is in contrast to
   * the [allowAccountsAccess] flag, which simply prevents access to the entire
   * settings UI.
   */

  val allowAccountsRegistryAccess: Boolean

  /**
   * If set to `true`, users will be shown the option to switch accounts on the
   * catalog screen.
   */

  val showChangeAccountsUi: Boolean

  /**
   * If `showChangeAccountsUi` is set to `false`, setting this to true will
   * still show the logo in the action bar. If `showChangeAccountsUi` is true,
   * this setting will have no effect.
   */

  val showActionBarLogo: Boolean

  /**
   * If set to `true`, users will be show age verification prompts and status on
   * the accounts screen.
   */

  val showAgeGateUi: Boolean

  /**
   * The list of ids of the supported featured libraries by this app.
   */

  val featuredLibrariesIdsList: List<String>
}
