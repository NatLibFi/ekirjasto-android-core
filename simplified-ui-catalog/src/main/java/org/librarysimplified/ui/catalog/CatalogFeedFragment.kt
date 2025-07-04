package org.librarysimplified.ui.catalog

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.View.TEXT_ALIGNMENT_TEXT_END
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Space
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.catalog.CatalogFeedOwnership.CollectedFromAccounts
import org.librarysimplified.ui.catalog.CatalogFeedOwnership.OwnedByAccount
import org.librarysimplified.ui.catalog.CatalogFeedState.CatalogFeedAgeGate
import org.librarysimplified.ui.catalog.CatalogFeedState.CatalogFeedLoadFailed
import org.librarysimplified.ui.catalog.CatalogFeedState.CatalogFeedLoaded
import org.librarysimplified.ui.catalog.CatalogFeedState.CatalogFeedLoaded.CatalogFeedEmpty
import org.librarysimplified.ui.catalog.CatalogFeedState.CatalogFeedLoaded.CatalogFeedNavigation
import org.librarysimplified.ui.catalog.CatalogFeedState.CatalogFeedLoaded.CatalogFeedWithGroups
import org.librarysimplified.ui.catalog.CatalogFeedState.CatalogFeedLoaded.CatalogFeedWithoutGroups
import org.librarysimplified.ui.catalog.CatalogFeedState.CatalogFeedLoading
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountLoginState
import org.nypl.simplified.android.ktx.supportActionBar
import org.nypl.simplified.books.covers.BookCoverProviderType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.feeds.api.FeedBooksSelection
import org.nypl.simplified.feeds.api.FeedFacet
import org.nypl.simplified.feeds.api.FeedFacets
import org.nypl.simplified.feeds.api.FeedGroup
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.listeners.api.fragmentListeners
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.accounts.AccountPickerDialogFragment
import org.nypl.simplified.ui.images.ImageAccountIcons
import org.nypl.simplified.ui.images.ImageLoaderType
import org.nypl.simplified.ui.screen.ScreenSizeInformationType
import org.slf4j.LoggerFactory
import org.thepalaceproject.theme.core.PalaceTabButtons
import org.thepalaceproject.theme.core.PalaceToolbar
import kotlin.math.roundToInt

/**
 * A fragment displaying an OPDS feed.
 */

class CatalogFeedFragment : Fragment(R.layout.feed), AgeGateDialog.BirthYearSelectedListener {

  companion object {

    private const val PARAMETERS_ID =
      "org.librarysimplified.ui.catalog.CatalogFragmentFeed.parameters"

    private val AGE_GATE_DIALOG_TAG =
      AgeGateDialog::class.java.simpleName

    /**
     * Create a catalog feed fragment for the given parameters.
     */

    fun create(parameters: CatalogFeedArguments): CatalogFeedFragment {
      val fragment = CatalogFeedFragment()
      fragment.arguments = bundleOf(PARAMETERS_ID to parameters)
      return fragment
    }
  }

  private val logger = LoggerFactory.getLogger(CatalogFeedFragment::class.java)

  private val parameters: CatalogFeedArguments by lazy {
    this.requireArguments()[PARAMETERS_ID] as CatalogFeedArguments
  }

  private val services =
    Services.serviceDirectory()

  private val listener: FragmentListenerType<CatalogFeedEvent> by fragmentListeners()

  private val borrowViewModel: CatalogBorrowViewModel by viewModels(
    factoryProducer = {
      CatalogBorrowViewModelFactory(services)
    }
  )

  private val viewModel: CatalogFeedViewModel by viewModels(
    factoryProducer = {
      CatalogFeedViewModelFactory(
        resources = this.requireActivity().resources,
        services = Services.serviceDirectory(),
        borrowViewModel = borrowViewModel,
        feedArguments = this.parameters,
        listener = this.listener
      )
    }
  )

  private val bookCovers =
    services.requireService(BookCoverProviderType::class.java)
  private val screenInformation =
    services.requireService(ScreenSizeInformationType::class.java)
  private val configurationService =
    services.requireService(BuildConfigurationServiceType::class.java)
  private val profilesController =
    services.requireService(ProfilesControllerType::class.java)
  private val imageLoader =
    services.requireService(ImageLoaderType::class.java)

  private lateinit var buttonCreator: CatalogButtons
  private lateinit var feedContent: ViewGroup
  private lateinit var feedError: ViewGroup
  private lateinit var feedErrorDetails: Button
  private lateinit var feedErrorRetry: Button
  private lateinit var feedLoading: ViewGroup
  private lateinit var feedNavigation: ViewGroup
  private lateinit var feedContentFacets: LinearLayout
  private lateinit var feedContentFacetsScroll: ViewGroup
  private lateinit var feedContentHeader: ViewGroup
  private lateinit var feedContentLogoHeader: ViewGroup
  private lateinit var feedContentLogoImage: ImageView
  private lateinit var feedContentLogoText: TextView
  private lateinit var feedEmptyMessage: TextView
  private lateinit var feedContentTabs: RadioGroup
  private lateinit var feedContentRefresh: SwipeRefreshLayout
  private lateinit var feedWithGroupsAdapter: CatalogFeedWithGroupsAdapter
  private lateinit var feedWithGroupsList: RecyclerView
  private lateinit var feedWithoutGroupsAdapter: CatalogPagedAdapter
  private lateinit var feedWithoutGroupsList: RecyclerView
  private lateinit var feedWithoutGroupsScrollListener: RecyclerView.OnScrollListener
  //Ellibs Dev
  //private lateinit var toolbar: NeutralToolbar
  private lateinit var toolbar: PalaceToolbar

  private var ageGateDialog: DialogFragment? = null
  private val feedWithGroupsData: MutableList<FeedGroup> = mutableListOf()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setHasOptionsMenu(true)

    this.ageGateDialog =
      childFragmentManager.findFragmentByTag(AGE_GATE_DIALOG_TAG) as? DialogFragment
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    this.toolbar =
      view.rootView.findViewWithTag(PalaceToolbar.palaceToolbarName)

    this.viewModel.stateLive.observe(this.viewLifecycleOwner, this::reconfigureUI)

    this.buttonCreator =
      CatalogButtons(this.requireContext(), this.screenInformation)

    this.feedError =
      view.findViewById(R.id.feedError)
    this.feedLoading =
      view.findViewById(R.id.feedLoading)
    this.feedNavigation =
      view.findViewById(R.id.feedNavigation)
    this.feedContent =
      view.findViewById(R.id.feedContent)

    this.feedContentHeader =
      view.findViewById(R.id.feedContentHeader)
    this.feedContentRefresh =
      view.findViewById(R.id.feedContentRefresh)
    this.feedContentFacetsScroll =
      this.feedContentHeader.findViewById(R.id.feedHeaderFacetsScroll)
    this.feedEmptyMessage =
      this.feedContent.findViewById(R.id.feedEmptyMessage)


    this.feedContentFacets =
      this.feedContentHeader.findViewById(R.id.feedHeaderFacets)
    this.feedContentTabs =
      this.feedContentHeader.findViewById(R.id.feedHeaderTabs)

    this.feedContentLogoHeader =
      this.feedContent.findViewById(R.id.feedContentLogoHeader)
    this.feedContentLogoImage =
      this.feedContent.findViewById(R.id.feedLibraryLogo)

    if (parameters is CatalogFeedArguments.CatalogFeedArgumentsLocalBooks) {
      feedContentLogoImage.visibility = View.GONE
    }

    this.feedContentLogoText =
      this.feedContent.findViewById(R.id.feedLibraryText)

    this.feedContentRefresh =
      this.feedContent.findViewById(R.id.feedContentRefresh)

    this.feedWithGroupsList = this.feedContent.findViewById(R.id.feedWithGroupsList)
    this.feedWithGroupsList.setHasFixedSize(true)
    this.feedWithGroupsList.setItemViewCacheSize(32)
    this.feedWithGroupsList.layoutManager = LinearLayoutManager(this.context)
    (this.feedWithGroupsList.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
    this.feedWithGroupsList.addItemDecoration(
      CatalogFeedWithGroupsDecorator(this.screenInformation.dpToPixels(16).toInt())
    )

    this.feedWithGroupsAdapter =
      CatalogFeedWithGroupsAdapter(
        groups = this.feedWithGroupsData,
        coverLoader = this.bookCovers,
        onFeedSelected = this.viewModel::openFeed,
        onBookSelected = this.viewModel::openBookDetail
      )
    this.feedWithGroupsList.adapter = this.feedWithGroupsAdapter
    this.feedWithoutGroupsList = this.feedContent.findViewById(R.id.feedWithoutGroupsList)
    this.feedWithoutGroupsList.setHasFixedSize(true)
    this.feedWithoutGroupsList.setItemViewCacheSize(32)
    this.feedWithoutGroupsList.layoutManager = LinearLayoutManager(this.context)
    (this.feedWithoutGroupsList.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

    this.feedErrorRetry =
      this.feedError.findViewById(R.id.feedErrorRetry)
    this.feedErrorDetails =
      this.feedError.findViewById(R.id.feedErrorDetails)

    this.feedContent.visibility = View.INVISIBLE
    this.feedError.visibility = View.INVISIBLE
    this.feedLoading.visibility = View.INVISIBLE
    this.feedNavigation.visibility = View.INVISIBLE

    this.feedContentRefresh.setOnRefreshListener {
      this.refresh()
      this.feedContentRefresh.isRefreshing = false
    }

    this.feedContentLogoHeader.setOnClickListener {
      this.openLogoLink()
    }

    this.reconfigureCatalogUI()
  }

  /*
   * Reconfigures the UI based on the log in status of the account.
   * This effects the texts shown on my books and favorites pages.
   * Triggered in onViewCreated and Start
   */
  private fun reconfigureCatalogUI() {
    //Get the current account's login state from viewModel
    val loginState = this.viewModel.account.loginState
    //Ask to login when not logged in or in between, and info about loans and reservations when logged in
    when (loginState){
      is AccountLoginState.AccountNotLoggedIn -> this.showLoginText()
      is AccountLoginState.AccountLoggedIn -> this.showInfoWhenLoggedIn()
      else -> this.showLoginText()
    }
  }

  //Account not logged in, so show text asking for log in
  private fun showLoginText() {
    if (parameters is CatalogFeedArguments.CatalogFeedArgumentsLocalBooks) {
      this.feedEmptyMessage.setText(
        //If selection is the selected books, show a suitable
        //Text, otherwise show default (should never happen in current shape)
        if (
          (parameters as CatalogFeedArguments.CatalogFeedArgumentsLocalBooks).selection ==
          FeedBooksSelection.BOOKS_FEED_SELECTED
        ) {
          R.string.emptySelectedNotLoggedIn
        } else {
          R.string.emptyBooksNotLoggedIn
        }
      )
    }
    //If user is viewing the multiple feed view, set the message to always be the same
    //No matter the view
    if (parameters is CatalogFeedArguments.CatalogFeedArgumentsAllLocalBooks) {
      this.feedEmptyMessage.setText(
        R.string.emptyBooksNotLoggedIn
      )
    }
  }

  //User is logged in, so we show text that informs about important things concerning loans and
  //reservations
  private fun showInfoWhenLoggedIn() {
    if (parameters is CatalogFeedArguments.CatalogFeedArgumentsLocalBooks) {
      this.feedEmptyMessage.setText(
        //Check if books shown are selected, and if the list is empty
        //show an info of how to add books to selected
        //Current layout should never show the default
        if (
          (parameters as CatalogFeedArguments.CatalogFeedArgumentsLocalBooks).selection ==
          FeedBooksSelection.BOOKS_FEED_SELECTED
        ) {
          R.string.feedWithGroupsEmptySelected
        } else {
          R.string.feedWithGroupsEmptyLoaned
        }
      )
    }
    //Set the feed empty messages for a multifeed view
    if (parameters is CatalogFeedArguments.CatalogFeedArgumentsAllLocalBooks) {
      this.feedEmptyMessage.setText(
        //Add a special text to loans and holds
        //Check the current selection from stateLive to show the correct message per view
      if (
        (viewModel.stateLive.value?.arguments as CatalogFeedArguments.CatalogFeedArgumentsAllLocalBooks).selection ==
        FeedBooksSelection.BOOKS_FEED_LOANED
      ) {
        R.string.feedWithGroupsEmptyLoaned
      } else {
        R.string.feedWithGroupsEmptyHolds
      }
      )
    }
  }

  private fun openLogoLink() {
    this.logger.debug("logo: attempting to open link")

    try {
      val accountProvider = this.viewModel.accountProvider
      if (accountProvider != null) {
        val alternate = accountProvider.alternateURI
        if (alternate != null) {
          val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(alternate.toString()))
          this.startActivity(browserIntent)
        } else {
          this.logger.debug("logo: no alternate link")
        }
      } else {
        this.logger.debug("logo: account provider was null")
      }
    } catch (e: Exception) {
      this.logger.error("logo: unable to handle alternate link: ", e)
    }
  }

  override fun onStart() {
    super.onStart()

    this.feedWithoutGroupsScrollListener = CatalogScrollListener(this.bookCovers)
    this.feedWithoutGroupsList.addOnScrollListener(this.feedWithoutGroupsScrollListener)

    this.reconfigureCatalogUI()
  }

  override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
    inflater.inflate(R.menu.catalog, menu)

    val search = menu.findItem(R.id.catalogMenuActionSearch)
    val searchView = search.actionView as SearchView

    searchView.imeOptions = EditorInfo.IME_ACTION_DONE
    searchView.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
    searchView.queryHint = getString(R.string.catalogSearch)
    searchView.maxWidth = toolbar.getAvailableWidthForSearchView()

    val currentQuery = when (parameters) {
      is CatalogFeedArguments.CatalogFeedArgumentsLocalBooks -> {
        (parameters as CatalogFeedArguments.CatalogFeedArgumentsLocalBooks).searchTerms.orEmpty()
      }
      is CatalogFeedArguments.CatalogFeedArgumentsAllLocalBooks -> {
        (parameters as CatalogFeedArguments.CatalogFeedArgumentsAllLocalBooks).searchTerms.orEmpty()
      }
      is CatalogFeedArguments.CatalogFeedArgumentsRemote -> {
        val uri =
          Uri.parse(
            (parameters as CatalogFeedArguments.CatalogFeedArgumentsRemote).feedURI.toString()
          )
        uri.getQueryParameter("q").orEmpty()
      }
    }

    searchView.setQuery(currentQuery, false)

    // if there's no query, the search should be iconified, i.e., collapsed
    searchView.isIconified = currentQuery.isBlank()

    searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
      override fun onQueryTextSubmit(query: String): Boolean {
        val viewModel = this@CatalogFeedFragment.viewModel
        viewModel.stateLive.value?.search?.let { search ->
          viewModel.performSearch(search, query)
        }
        searchView.clearFocus()
        return true
      }

      override fun onQueryTextChange(newText: String): Boolean {
        return true
      }
    })

    searchView.clearFocus()
  }

  override fun onPrepareOptionsMenu(menu: Menu) {
    super.onPrepareOptionsMenu(menu)

    // Necessary to reconfigure the Toolbar here due to the "Switch Account" action.
    this.configureToolbar()
  }

  private fun refresh() {
    this.viewModel.syncAccounts()
    this.viewModel.reloadFeed()
  }

  private fun reconfigureUI(feedState: CatalogFeedState) {
    return when (feedState) {
      is CatalogFeedAgeGate ->
        this.onCatalogFeedAgeGate(feedState)
      is CatalogFeedLoading ->
        this.onCatalogFeedLoading(feedState)
      is CatalogFeedWithGroups ->
        this.onCatalogFeedWithGroups(feedState)
      is CatalogFeedWithoutGroups ->
        this.onCatalogFeedWithoutGroups(feedState)
      is CatalogFeedNavigation ->
        this.onCatalogFeedNavigation(feedState)
      is CatalogFeedLoadFailed ->
        this.onCatalogFeedLoadFailed(feedState)
      is CatalogFeedEmpty ->
        this.onCatalogFeedEmpty(feedState)
    }
  }

  override fun onStop() {
    super.onStop()

    /*
     * We aggressively unset adapters here in order to try to encourage prompt unsubscription
     * of views from the book registry.
     */

    this.feedWithoutGroupsList.removeOnScrollListener(this.feedWithoutGroupsScrollListener)
  }

  override fun onDestroyView() {
    super.onDestroyView()
    this.feedWithoutGroupsList.adapter = null
    this.feedWithGroupsList.adapter = null
  }

  private fun onCatalogFeedAgeGate(
    @Suppress("UNUSED_PARAMETER") feedState: CatalogFeedAgeGate
  ) {
    this.openAgeGateDialog()
    this.feedContent.visibility = View.INVISIBLE
    this.feedError.visibility = View.INVISIBLE
    this.feedLoading.visibility = View.INVISIBLE
    this.feedNavigation.visibility = View.INVISIBLE

    this.configureToolbar()
  }

  private fun onCatalogFeedEmpty(
    @Suppress("UNUSED_PARAMETER") feedState: CatalogFeedEmpty
  ) {
    this.dismissAgeGateDialog()
    this.feedContent.visibility = View.VISIBLE
    this.feedEmptyMessage.visibility = View.VISIBLE
    this.feedContentHeader.visibility = View.GONE
    this.feedWithGroupsList.visibility = View.INVISIBLE
    this.feedWithoutGroupsList.visibility = View.INVISIBLE
    this.feedError.visibility = View.INVISIBLE
    this.feedLoading.visibility = View.INVISIBLE
    this.feedNavigation.visibility = View.INVISIBLE

    //If we are viewing multiple feed view, we want to show the top facets even if
    //The feed is empty
    if (feedState.arguments is CatalogFeedArguments.CatalogFeedArgumentsAllLocalBooks) {
      if (feedState.facetsByGroup != null) {
        // If there are some top level facets,configure them so they are shown on an empty view
        this.configureFacetTabs(FeedFacets.findEntryPointFacetGroup(feedState.facetsByGroup), feedContentTabs)
        //Update catalog UI to have the texts be up to date
        this.reconfigureCatalogUI()
        feedContentHeader.visibility = View.VISIBLE
      }
    }
    this.configureLogoHeader(feedState)
    this.configureToolbar()
  }

  private fun onCatalogFeedLoading(
    @Suppress("UNUSED_PARAMETER") feedState: CatalogFeedLoading
  ) {
    this.dismissAgeGateDialog()
    this.feedContent.visibility = View.INVISIBLE
    this.feedError.visibility = View.INVISIBLE
    this.feedLoading.visibility = View.VISIBLE
    this.feedNavigation.visibility = View.INVISIBLE

    this.configureToolbar()
  }

  private fun onCatalogFeedNavigation(
    @Suppress("UNUSED_PARAMETER") feedState: CatalogFeedNavigation
  ) {
    this.dismissAgeGateDialog()
    this.feedContent.visibility = View.INVISIBLE
    this.feedError.visibility = View.INVISIBLE
    this.feedLoading.visibility = View.INVISIBLE
    this.feedNavigation.visibility = View.VISIBLE

    this.configureLogoHeader(feedState)
    this.configureToolbar()
  }

  private fun onCatalogFeedWithoutGroups(
    feedState: CatalogFeedWithoutGroups
  ) {
    this.dismissAgeGateDialog()
    this.feedContent.visibility = View.VISIBLE
    this.feedEmptyMessage.visibility = View.INVISIBLE
    this.feedError.visibility = View.INVISIBLE
    this.feedLoading.visibility = View.INVISIBLE
    this.feedNavigation.visibility = View.INVISIBLE
    this.feedWithGroupsList.visibility = View.INVISIBLE
    this.feedWithoutGroupsList.visibility = View.VISIBLE

    this.configureToolbar()
    this.configureFacets(
      facetsByGroup = feedState.facetsByGroup,
      sortFacets = false
    )
    this.configureLogoHeader(feedState)

    this.feedWithoutGroupsAdapter =
      CatalogPagedAdapter(
        context = requireActivity(),
        listener = this.viewModel,
        buttonCreator = this.buttonCreator,
        bookCovers = this.bookCovers,
        profilesController = this.profilesController
      )

    this.feedWithoutGroupsList.adapter = this.feedWithoutGroupsAdapter
    feedState.entries.observe(this) { newPagedList ->
      this.logger.debug("received paged list ({} elements)", newPagedList.size)
      this.feedWithoutGroupsAdapter.submitList(newPagedList)
    }
  }

  private fun onCatalogFeedWithGroups(
    feedState: CatalogFeedWithGroups
  ) {
    this.dismissAgeGateDialog()
    this.feedContent.visibility = View.VISIBLE
    this.feedEmptyMessage.visibility = View.INVISIBLE
    this.feedError.visibility = View.INVISIBLE
    this.feedLoading.visibility = View.INVISIBLE
    this.feedNavigation.visibility = View.INVISIBLE
    this.feedWithGroupsList.visibility = View.VISIBLE
    this.feedWithoutGroupsList.visibility = View.INVISIBLE

    this.configureToolbar()
    this.configureFacets(
      facetsByGroup = feedState.feed.facetsByGroup,
      sortFacets = true
    )
    this.configureLogoHeader(feedState)

    this.feedWithGroupsData.clear()
    this.feedWithGroupsData.addAll(feedState.feed.feedGroupsInOrder)
    this.feedWithGroupsAdapter.notifyDataSetChanged()
  }

  private fun configureLogoHeader(feedState: CatalogFeedLoaded) {
    fun loadImageAndText(accountId: AccountID) {
      try {
        val account =
          this.profilesController.profileCurrent()
            .account(accountId)

        ImageAccountIcons.loadAccountLogoIntoView(
          loader = this.imageLoader.loader,
          account = account.provider.toDescription(),
          defaultIcon = R.drawable.header_logo,
          iconView = feedContentLogoImage
        )

        feedContentLogoText.text = ""
      } catch (e: Exception) {
        this.logger.debug("error configuring header: ", e)
      }
    }

    when (feedState) {
      is CatalogFeedNavigation -> {
        // do nothing
      }
      else -> {
        when (val ownership = feedState.arguments.ownership) {
          CollectedFromAccounts -> {
            this.feedContentLogoHeader.visibility = View.GONE
          }
          is OwnedByAccount -> {
            this.feedContentLogoHeader.visibility = View.GONE
            loadImageAndText(accountId = ownership.accountId)
          }
        }
      }
    }
  }

  private fun onCatalogFeedLoadFailed(
    feedState: CatalogFeedLoadFailed
  ) {
    this.dismissAgeGateDialog()
    this.feedContent.visibility = View.INVISIBLE
    this.feedError.visibility = View.VISIBLE
    this.feedLoading.visibility = View.INVISIBLE
    this.feedNavigation.visibility = View.INVISIBLE

    this.configureToolbar()

    this.feedErrorRetry.isEnabled = true
    this.feedErrorRetry.setOnClickListener { button ->
      button.isEnabled = false
      this.viewModel.reloadFeed()
    }

    this.feedErrorDetails.isEnabled = true
    this.feedErrorDetails.setOnClickListener {
      this.viewModel.showFeedErrorDetails(feedState.failure)
    }
  }

  private fun configureToolbar() {
    try {
      this.toolbar.title = this.viewModel.title()
      val actionBar = this.supportActionBar ?: return
      actionBar.show()

      /*
       * If we're not at the root of a feed, then display a back arrow in the toolbar.
       */

      if (!this.viewModel.isAccountCatalogRoot()) {
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBar.setHomeActionContentDescription(null)
        this.toolbar.setLogoOnClickListener {
          this.viewModel.goUpwards()
        }
        return
      }

      /*
       * If we're at the root of a feed and the app is configured such that the user should
       * be allowed to change accounts, then display the current account's logo in the toolbar.
       */

      if (this.configurationService.showChangeAccountsUi) {
        actionBar.setHomeActionContentDescription(R.string.catalogAccounts)
        actionBar.setLogo(this.configurationService.brandingAppIcon)
        this.toolbar.setLogoOnClickListener {
          this.openAccountPickerDialog()
        }
        return
      }

      /*
       * If the change accounts UI is disabled, check if logo should be shown.
       */
      if (this.configurationService.showActionBarLogo) {
        // Show the logo, make it go back to the catalog ("Browse books" tab)
        actionBar.setLogo(this.configurationService.brandingAppIcon)
        actionBar.setDisplayHomeAsUpEnabled(false)
        this.toolbar.setLogoOnClickListener {
          this.viewModel.goUpwards()
        }
        return
      }

      /*
       * Otherwise, show nothing.
       */

      actionBar.setDisplayHomeAsUpEnabled(false)
      actionBar.setLogo(null)

      this.toolbar.setLogoOnClickListener {
        // Do nothing
      }
    } catch (e: Exception) {
      // Nothing to do
    }
  }

  private fun openAccountPickerDialog() {
    try {
      return when (val ownership = this.parameters.ownership) {
        is OwnedByAccount -> {
          val dialog =
            AccountPickerDialogFragment.create(
              currentId = ownership.accountId,
              showAddAccount = this.configurationService.allowAccountsAccess
            )
          dialog.show(parentFragmentManager, dialog.tag)
        }
        CollectedFromAccounts -> {
          throw IllegalStateException("Can't switch account from collected feed!")
        }
      }
    } catch (e: Exception) {
      this.logger.error("Failed to open account picker dialog: ", e)
    }
  }

  private fun configureFacets(
    facetsByGroup: Map<String, List<FeedFacet>>,
    sortFacets: Boolean
  ) {
    /*
     * If the facet groups are empty, hide the header entirely.
     */

    if (facetsByGroup.isEmpty()) {
      feedContentHeader.visibility = View.GONE
      return
    }

    /*
     * If one of the groups is an entry point, display it as a set of tabs. Otherwise, hide
     * the tab layout entirely.
     */

    this.configureFacetTabs(FeedFacets.findEntryPointFacetGroup(facetsByGroup), feedContentTabs)

    /*
     * Otherwise, for each remaining non-entrypoint facet group, show a drop-down menu allowing
     * the selection of individual facets. If there are no remaining groups, hide the button
     * bar.
     */

    val remainingGroups = facetsByGroup
      .filter { entry ->
        /*
         * Hide the 'Collection' Facet since we only have one library
         */
        entry.key != "Collection" &&
        entry.key != "Kokoelma" &&
        entry.key != "Samling"
      }
      .filter { entry ->
        !FeedFacets.facetGroupIsEntryPointTyped(entry.value)
      }

    if (remainingGroups.isEmpty()) {
      feedContentFacetsScroll.visibility = View.GONE
      return
    }

    val buttonLayoutParams =
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )

    val textLayoutParams =
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )

    textLayoutParams.gravity = Gravity.END or Gravity.CENTER_VERTICAL

    val spacerLayoutParams =
      LinearLayout.LayoutParams(
        this.screenInformation.dpToPixels(8).toInt(),
        LinearLayout.LayoutParams.MATCH_PARENT
      )

    val sortedNames = if (sortFacets) {
      remainingGroups.keys.sorted()
    } else {
      remainingGroups.keys
    }
    val context = this.requireContext()

    feedContentFacets.removeAllViews()
    sortedNames.forEach { groupName ->
      val group = remainingGroups.getValue(groupName)
      if (FeedFacets.facetGroupIsEntryPointTyped(group)) {
        return@forEach
      }

      val button = MaterialButton(context)
      val buttonLabel = AppCompatTextView(context)
      val spaceStart = Space(context)
      val spaceMiddle = Space(context)
      val spaceEnd = Space(context)

      val active =
        group.find { facet -> facet.isActive }
          ?: group.firstOrNull()

      button.id = View.generateViewId()
      //ellibs dev TODO: Refactor?
      button.setCompoundDrawablesWithIntrinsicBounds(0,0, R.drawable.catalog_facet_button_icon,0)
      button.compoundDrawablePadding = resources.getDimension(R.dimen.catalogFacetButtonIconPadding).roundToInt();
      button.layoutParams = buttonLayoutParams
      button.text = active?.title
      button.ellipsize = TextUtils.TruncateAt.END
      button.setOnClickListener {
        this.showFacetSelectDialog(groupName, group)
      }

      spaceStart.layoutParams = spacerLayoutParams
      spaceMiddle.layoutParams = spacerLayoutParams
      spaceEnd.layoutParams = spacerLayoutParams

      buttonLabel.layoutParams = textLayoutParams
      buttonLabel.text = "$groupName: "
      buttonLabel.labelFor = button.id
      buttonLabel.maxLines = 1
      buttonLabel.ellipsize = TextUtils.TruncateAt.END
      buttonLabel.textAlignment = TEXT_ALIGNMENT_TEXT_END
      buttonLabel.gravity = Gravity.END or Gravity.CENTER_VERTICAL

      feedContentFacets.addView(spaceStart)
      feedContentFacets.addView(buttonLabel)
      feedContentFacets.addView(spaceMiddle)
      feedContentFacets.addView(button)
      feedContentFacets.addView(spaceEnd)
    }

    feedContentFacetsScroll.scrollTo(0, 0)
  }

  private fun configureFacetTabs(
    facetGroup: List<FeedFacet>?,
    facetTabs: RadioGroup
  ) {
    if (facetGroup == null) {
      facetTabs.visibility = View.GONE
      return
    }

    facetTabs.removeAllViews()
    PalaceTabButtons.configureGroup(
      context = this.requireContext(),
      group = facetTabs,
      count = facetGroup.size
    ) { index, button ->
      val facet = facetGroup[index]
      button.text = facet.title
      button.setOnClickListener {
        this.logger.debug("selected entry point facet: {}", facet.title)
        this.viewModel.openFacet(facet)
        updateSelectedFacet(facetTabs = facetTabs, index = index)
      }
    }

    /*
     * Uncheck all of the buttons, and then check the one that corresponds to the current
     * active facet.
     */

    facetTabs.clearCheck()

    for (index in 0 until facetGroup.size) {
      val facet = facetGroup[index]
      val button = facetTabs.getChildAt(index) as RadioButton

      if (facet.isActive) {
        this.logger.debug("active entry point facet: {}", facet.title)
        facetTabs.check(button.id)
      }
    }
  }

  private fun updateSelectedFacet(facetTabs: RadioGroup, index: Int) {
    facetTabs.clearCheck()
    val button = facetTabs.getChildAt(index) as RadioButton
    facetTabs.check(button.id)
  }

  private fun showFacetSelectDialog(
    groupName: String,
    group: List<FeedFacet>
  ) {
    val choices = group.sortedBy { it.title }
    val checkedItem = choices.indexOfFirst { it.isActive }

    // Build the dialog
    val alertBuilder = MaterialAlertDialogBuilder(this.requireContext())
    alertBuilder.setTitle(groupName)

    //Get adaptor to add the contentDescriptions to the choices
    val adapter = CatalogFacetAdapter(this.requireContext(), groupName, choices)
    alertBuilder.setSingleChoiceItems(adapter, checkedItem) { dialog, checked ->
      val selected = choices[checked]
      this.logger.debug("selected facet: {}", selected)
      this.viewModel.openFacet(selected)
      dialog.dismiss()
    }
    alertBuilder.create().show()
  }

  override fun onBirthYearSelected(isOver13: Boolean) {
    this.viewModel.updateBirthYear(isOver13)
  }

  private fun openAgeGateDialog() {
    if (this.ageGateDialog != null) {
      return
    }

    val ageGate = AgeGateDialog.create()
    ageGate.show(childFragmentManager, AGE_GATE_DIALOG_TAG)
    this.ageGateDialog = ageGate
  }

  private fun dismissAgeGateDialog() {
    this.ageGateDialog?.dismiss()
    this.ageGateDialog = null
  }
}
