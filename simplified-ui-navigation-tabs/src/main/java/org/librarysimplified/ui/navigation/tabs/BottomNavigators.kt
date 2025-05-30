package org.librarysimplified.ui.navigation.tabs

import android.content.Context
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import com.io7m.junreachable.UnreachableCodeException
import com.pandora.bottomnavigator.BottomNavigator
import org.joda.time.DateTime
import org.librarysimplified.ui.catalog.CatalogFeedArguments
import org.librarysimplified.ui.catalog.CatalogFeedFragment
import org.librarysimplified.ui.catalog.CatalogFeedOwnership
import fi.kansalliskirjasto.ekirjasto.magazines.MagazinesArguments
import fi.kansalliskirjasto.ekirjasto.magazines.MagazinesFragment
import org.librarysimplified.ui.tabs.R
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.feeds.api.FeedBooksSelection
import org.nypl.simplified.feeds.api.FeedFacet
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.accounts.AccountFragmentParameters
import org.nypl.simplified.ui.accounts.ekirjasto.EKirjastoAccountFragment
import org.slf4j.LoggerFactory

object BottomNavigators {

  private val logger = LoggerFactory.getLogger(BottomNavigators::class.java)

  /**
   * Create a new tabbed navigation controller. The controller will load fragments into the
   * fragment container specified by [fragmentContainerId], using the Pandora BottomNavigator
   * view [navigationView].
   */

  fun create(
    fragment: Fragment,
    @IdRes fragmentContainerId: Int,
    navigationView: BottomNavigationView,
    accountProviders: AccountProviderRegistryType,
    profilesController: ProfilesControllerType,
    settingsConfiguration: BuildConfigurationServiceType,
  ): BottomNavigator {
    logger.debug("creating bottom navigator")

    val context =
      fragment.requireContext()

    val navigator =
      BottomNavigator.onCreate(
        fragmentContainer = fragmentContainerId,
        bottomNavigationView = navigationView,
        rootFragmentsFactory = mapOf(
          R.id.tabCatalog to {
            createCatalogFragment(
              id = R.id.tabCatalog,
              feedArguments = catalogFeedArguments(
                context,
                profilesController,
                accountProviders.defaultProvider
              )
            )
          },
          R.id.tabBooks to {
            createCombinationFragment(
              context = context,
              id = R.id.tabBooks,
              profilesController = profilesController,
              settingsConfiguration = settingsConfiguration,
              defaultProvider = accountProviders.defaultProvider
            )
          },
          R.id.tabSelected to {
            createSelectedFragment(
              context = context,
              id = R.id.tabSelected,
              profilesController = profilesController,
              settingsConfiguration = settingsConfiguration,
              defaultProvider = accountProviders.defaultProvider
            )
          },
          R.id.tabMagazines to {
            createMagazinesFragment(
              id = R.id.tabMagazines,
            )
          },
          R.id.tabSettings to {
            createSettingsFragment(
              R.id.tabSettings,
              profilesController,
              accountProviders.defaultProvider)
          }
        ),
        defaultTab = R.id.tabCatalog,
        fragment = fragment,
        instanceOwner = fragment
      )

    navigationView.labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED

    return navigator
  }

  private fun currentAge(
    profilesController: ProfilesControllerType
  ): Int {
    return try {
      val profile = profilesController.profileCurrent()
      profile.preferences().dateOfBirth?.yearsOld(DateTime.now()) ?: 1
    } catch (e: Exception) {
      logger.error("could not retrieve profile age: ", e)
      1
    }
  }

  private fun pickDefaultAccount(
    profilesController: ProfilesControllerType,
    defaultProvider: AccountProviderType
  ): AccountType {
    val profile = profilesController.profileCurrent()
    val mostRecentId = profile.preferences().mostRecentAccount
    if (mostRecentId != null) {
      try {
        return profile.account(mostRecentId)
      } catch (e: Exception) {
        logger.error("stale account: ", e)
      }
    }

    val accounts = profile.accounts().values
    return when {
      accounts.size > 1 -> {
        // Return the first account created from a non-default provider
        accounts.first { it.provider.id != defaultProvider.id }
      }
      accounts.size == 1 -> {
        // Return the first account
        accounts.first()
      }
      else -> {
        // There should always be at least one account
        throw UnreachableCodeException()
      }
    }
  }

  private fun catalogFeedArguments(
    context: Context,
    profilesController: ProfilesControllerType,
    defaultProvider: AccountProviderType
  ): CatalogFeedArguments.CatalogFeedArgumentsRemote {
    val age = currentAge(profilesController)
    val account = pickDefaultAccount(profilesController, defaultProvider)
    return CatalogFeedArguments.CatalogFeedArgumentsRemote(
      ownership = CatalogFeedOwnership.OwnedByAccount(account.id),
      feedURI = account.catalogURIForAge(age),
      isSearchResults = false,
      title = context.getString(R.string.tabCatalog)
    )
  }

  private fun createSettingsFragment(
    id: Int,
    profilesController: ProfilesControllerType,
    defaultProvider: AccountProviderType
    ): Fragment {
    val account = pickDefaultAccount(profilesController, defaultProvider)
    logger.debug("[{}]: creating settings fragment", id)
    return EKirjastoAccountFragment.create(AccountFragmentParameters(
      accountID = account.id,
      showPleaseLogInTitle = false,
      hideToolbar = false,
      barcode = null
    ))
  }

  private fun createMagazinesFragment(
    id: Int,
  ): Fragment {
    logger.debug("[{}]: creating magazines fragment", id)
    return MagazinesFragment.create(
      MagazinesArguments.MagazinesArgumentsData(
        token = null,
      )
    )
  }

  private fun createHoldsFragment(
    context: Context,
    id: Int,
    profilesController: ProfilesControllerType,
    settingsConfiguration: BuildConfigurationServiceType,
    defaultProvider: AccountProviderType
  ): Fragment {
    logger.debug("[{}]: creating holds fragment", id)

    /*
     * SIMPLY-2923: Filter by the default account until 'All' view is approved by UX.
     */

    val filterAccountId =
      if (settingsConfiguration.showBooksFromAllAccounts) {
        null
      } else {
        pickDefaultAccount(profilesController, defaultProvider).id
      }

    val ownership =
      if (filterAccountId == null) {
        CatalogFeedOwnership.CollectedFromAccounts
      } else {
        CatalogFeedOwnership.OwnedByAccount(filterAccountId)
      }

    return CatalogFeedFragment.create(
      CatalogFeedArguments.CatalogFeedArgumentsLocalBooks(
        filterAccount = filterAccountId,
        ownership = ownership,
        searchTerms = null,
        selection = FeedBooksSelection.BOOKS_FEED_HOLDS,
        sortBy = FeedFacet.FeedFacetPseudo.Sorting.SortBy.SORT_BY_TITLE,
        title = context.getString(R.string.tabHolds),
        updateHolds = true
      )
    )
  }

  /**
   * Create a fragment for showing the loaned books.
   */
  private fun createLoansFragment(
    context: Context,
    id: Int,
    profilesController: ProfilesControllerType,
    settingsConfiguration: BuildConfigurationServiceType,
    defaultProvider: AccountProviderType
  ): Fragment {
    logger.debug("[{}]: creating books fragment", id)

    /*
     * SIMPLY-2923: Filter by the default account until 'All' view is approved by UX.
     */

    val filterAccountId =
      if (settingsConfiguration.showBooksFromAllAccounts) {
        null
      } else {
        pickDefaultAccount(profilesController, defaultProvider).id
      }

    val ownership =
      if (filterAccountId == null) {
        CatalogFeedOwnership.CollectedFromAccounts
      } else {
        CatalogFeedOwnership.OwnedByAccount(filterAccountId)
      }

    return CatalogFeedFragment.create(
      CatalogFeedArguments.CatalogFeedArgumentsLocalBooks(
        filterAccount = filterAccountId,
        ownership = ownership,
        searchTerms = null,
        selection = FeedBooksSelection.BOOKS_FEED_LOANED,
        sortBy = FeedFacet.FeedFacetPseudo.Sorting.SortBy.SORT_BY_TITLE,
        title = context.getString(R.string.tabBooks),
        updateHolds = false
      )
    )
  }

  /**
   * Create a fragment for showing the selected books.
   */
  private fun createSelectedFragment(
    context: Context,
    id: Int,
    profilesController: ProfilesControllerType,
    settingsConfiguration: BuildConfigurationServiceType,
    defaultProvider: AccountProviderType
  ): Fragment {
    logger.debug("[{}]: creating selected fragment", id)

    //Choose the account we want to use, in our case, currently always null
    val filterAccountId =
      if (settingsConfiguration.showBooksFromAllAccounts) {
        null
      } else {
        pickDefaultAccount(profilesController, defaultProvider).id
      }

    //Choose the feed owner, currently we always collect from all accounts
    val ownership =
      if (filterAccountId == null) {
        CatalogFeedOwnership.CollectedFromAccounts
      } else {
        CatalogFeedOwnership.OwnedByAccount(filterAccountId)
      }

    //Create the fragment having the selection be the selected
    return CatalogFeedFragment.create(
      CatalogFeedArguments.CatalogFeedArgumentsLocalBooks(
        filterAccount = filterAccountId,
        ownership = ownership,
        searchTerms = null,
        selection = FeedBooksSelection.BOOKS_FEED_SELECTED,
        sortBy = FeedFacet.FeedFacetPseudo.Sorting.SortBy.SORT_BY_TITLE,
        title = context.getString(R.string.tabSelected),
        updateHolds = false
      )
    )
  }

  /**
   * Create a new fragment from the bookRegister with the option to switch between
   * multiple different views, like loans and selected books.
   */
  private fun createCombinationFragment(
    context: Context,
    id: Int,
    profilesController: ProfilesControllerType,
    settingsConfiguration: BuildConfigurationServiceType,
    defaultProvider: AccountProviderType
  ): Fragment {
    logger.debug("[{}]: creating combination fragment", id)

    //Check if should show all books in the registry or the books of one account
    val filterAccountId =
      if (settingsConfiguration.showBooksFromAllAccounts) {
        null
      } else {
        pickDefaultAccount(profilesController, defaultProvider).id
      }
    // Choose the owner, should be the ID of the account currently logged in
    val ownership =
      if (filterAccountId == null) {
        CatalogFeedOwnership.CollectedFromAccounts
      } else {
        CatalogFeedOwnership.OwnedByAccount(filterAccountId)
      }
    //Create the fragment, enter from the loans
    return CatalogFeedFragment.create(
      CatalogFeedArguments.CatalogFeedArgumentsAllLocalBooks(
        filterAccount = filterAccountId,
        ownership = ownership,
        searchTerms = null,
        sortBy = FeedFacet.FeedFacetPseudo.Sorting.SortBy.SORT_BY_TITLE,
        filterBy = FeedFacet.FeedFacetPseudo.FilteringForFeed.FilterBy.FILTER_BY_LOANS,
        selection = FeedBooksSelection.BOOKS_FEED_LOANED,
        title = context.getString(R.string.tabBooks),
        updateHolds = false
      )
    )
  }
  private fun createCatalogFragment(
    id: Int,
    feedArguments: CatalogFeedArguments.CatalogFeedArgumentsRemote
  ): Fragment {
    logger.debug("[{}]: creating catalog fragment", id)
    return CatalogFeedFragment.create(feedArguments)
  }
}
