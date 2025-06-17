package org.librarysimplified.ui.catalog

import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.text.Html
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.common.base.Preconditions
import com.google.common.util.concurrent.FluentFuture
import com.io7m.jfunctional.Some
import org.joda.time.DateTime
import org.joda.time.Duration
import org.joda.time.format.DateTimeFormatterBuilder
import org.librarysimplified.services.api.Services
import org.nypl.simplified.android.ktx.supportActionBar
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.book_database.api.BookFormats
import org.nypl.simplified.books.book_registry.BookPreviewStatus
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookWithStatus
import org.nypl.simplified.books.covers.BookCoverProviderType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.feeds.api.FeedEntry.FeedEntryOPDS
import org.nypl.simplified.feeds.api.FeedGroup
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.listeners.api.fragmentListeners
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.screen.ScreenSizeInformationType
import org.slf4j.LoggerFactory
import org.thepalaceproject.theme.core.PalaceToolbar
import java.io.File
import java.net.URI

/**
 * A book detail page.
 */

class CatalogBookDetailFragment : Fragment(R.layout.book_detail) {

  companion object {

    private const val PARAMETERS_ID =
      "org.librarysimplified.ui.catalog.CatalogFragmentBookDetail.parameters"

    /**
     * Create a book detail fragment for the given parameters.
     */

    fun create(parameters: CatalogBookDetailFragmentParameters): CatalogBookDetailFragment {
      val fragment = CatalogBookDetailFragment()
      fragment.arguments = bundleOf(this.PARAMETERS_ID to parameters)
      return fragment
    }
  }

  private val logger = LoggerFactory.getLogger(CatalogBookDetailFragment::class.java)

  private val services =
    Services.serviceDirectory()

  private val bookCovers =
    services.requireService(BookCoverProviderType::class.java)

  private val profilesController =
    services.requireService(ProfilesControllerType::class.java)

  private val screenInformation =
    services.requireService(ScreenSizeInformationType::class.java)

  private val parameters: CatalogBookDetailFragmentParameters by lazy {
    this.requireArguments()[PARAMETERS_ID] as CatalogBookDetailFragmentParameters
  }

  private val listener: FragmentListenerType<CatalogBookDetailEvent> by fragmentListeners()

  private val borrowViewModel: CatalogBorrowViewModel by viewModels(
    factoryProducer = {
      CatalogBorrowViewModelFactory(services)
    }
  )

  private val viewModel: CatalogBookDetailViewModel by viewModels(
    factoryProducer = {
      CatalogBookDetailViewModelFactory(
        this.services,
        this.borrowViewModel,
        this.listener,
        this.parameters
      )
    }
  )

  private var thumbnailLoading: FluentFuture<Unit>? = null

  private lateinit var authors: TextView
  private lateinit var buildConfig: BuildConfigurationServiceType
  private lateinit var buttonCreator: CatalogButtons
  private lateinit var buttons: LinearLayout
  private lateinit var cover: ImageView
  private lateinit var covers: BookCoverProviderType
  private lateinit var debugStatus: TextView
  private lateinit var feedWithGroupsAdapter: CatalogFeedWithGroupsAdapter
  private lateinit var feedWithoutGroupsAdapter: CatalogPagedAdapter
  private lateinit var metadata: LinearLayout
  private lateinit var relatedBooksContainer: FrameLayout
  private lateinit var relatedBooksList: RecyclerView
  private lateinit var relatedBooksLoading: ViewGroup
  private lateinit var report: TextView
  private lateinit var screenSize: ScreenSizeInformationType
  private lateinit var seeMore: TextView
  private lateinit var status: ViewGroup
  private lateinit var statusFailed: ViewGroup
  private lateinit var statusFailedText: TextView
  private lateinit var statusIdle: ViewGroup
  private lateinit var statusIdleText: TextView
  private lateinit var statusInProgress: ViewGroup
  private lateinit var statusInProgressBar: ProgressBar
  private lateinit var statusInProgressText: TextView
  private lateinit var summary: TextView
  private lateinit var title: TextView
  private lateinit var type: TextView
  private lateinit var selected: ImageView
  private lateinit var toolbar: PalaceToolbar

  private val dateYearFormatter =
    DateTimeFormatterBuilder()
      .appendYear(4, 5)
      .toFormatter()

  private val dateFormatter =
    DateTimeFormatterBuilder()
      .appendYear(4, 5)
      .appendLiteral('-')
      .appendMonthOfYear(2)
      .appendLiteral('-')
      .appendDayOfMonth(2)
      .toFormatter()

  private val dateTimeFormatter =
    DateTimeFormatterBuilder()
      .appendYear(4, 5)
      .appendLiteral('-')
      .appendMonthOfYear(2)
      .appendLiteral('-')
      .appendDayOfMonth(2)
      .appendLiteral(' ')
      .appendHourOfDay(2)
      .appendLiteral(':')
      .appendMinuteOfHour(2)
      .appendLiteral(':')
      .appendSecondOfMinute(2)
      .toFormatter()

  private val feedWithGroupsData: MutableList<FeedGroup> = mutableListOf()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    this.screenSize =
      services.requireService(ScreenSizeInformationType::class.java)
    this.covers =
      services.requireService(BookCoverProviderType::class.java)
    this.buildConfig =
      services.requireService(BuildConfigurationServiceType::class.java)

    this.buttonCreator =
      CatalogButtons(this.requireContext(), this.screenSize)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    this.toolbar =
      view.rootView.findViewWithTag(PalaceToolbar.palaceToolbarName)

    this.viewModel.bookWithStatusLive.observe(this.viewLifecycleOwner) { info ->
      reconfigureUI(info.first, info.second)
    }
    this.cover =
      view.findViewById(R.id.bookDetailCoverImage)
    this.title =
      view.findViewById(R.id.bookDetailTitle)
    this.type =
      view.findViewById(R.id.bookDetailType)
    this.authors =
      view.findViewById(R.id.bookDetailAuthors)
    this.seeMore =
      view.findViewById(R.id.seeMoreText)
    this.status =
      view.findViewById(R.id.bookDetailStatus)
    this.summary =
      view.findViewById(R.id.bookDetailDescriptionText)
    this.metadata =
      view.findViewById(R.id.bookDetailMetadataTable)
    this.buttons =
      view.findViewById(R.id.bookDetailButtons)
    this.relatedBooksContainer =
      view.findViewById(R.id.bookDetailRelatedBooksContainer)
    this.relatedBooksList =
      view.findViewById(R.id.relatedBooksList)
    this.relatedBooksLoading =
      view.findViewById(R.id.feedLoading)
    this.report =
      view.findViewById(R.id.bookDetailReport)
    this.selected =
      view.findViewById(R.id.bookDetailSelect)

    this.debugStatus =
      view.findViewById(R.id.bookDetailDebugStatus)

    this.statusIdle =
      this.status.findViewById(R.id.bookDetailStatusIdle)
    this.statusIdleText =
      this.statusIdle.findViewById(R.id.idleText)

    this.statusInProgress =
      this.status.findViewById(R.id.bookDetailStatusInProgress)
    this.statusInProgressBar =
      this.statusInProgress.findViewById(R.id.inProgressBar)
    this.statusInProgressText =
      this.statusInProgress.findViewById(R.id.inProgressText)

    this.statusInProgressText.text = "100%"

    this.statusFailed =
      this.status.findViewById(R.id.bookDetailStatusFailed)
    this.statusFailedText =
      this.status.findViewById(R.id.failedText)

    this.statusIdle.visibility = View.VISIBLE
    this.statusInProgress.visibility = View.INVISIBLE
    this.statusFailed.visibility = View.INVISIBLE

    this.debugStatus.visibility =
      if (this.viewModel.showDebugBookDetailStatus) {
        View.VISIBLE
      } else {
        View.INVISIBLE
      }

    val targetHeight =
      this.resources.getDimensionPixelSize(org.librarysimplified.books.covers.R.dimen.cover_detail_height)
    this.covers.loadCoverInto(
      this.parameters.feedEntry, this.cover, hasBadge = true, 0, targetHeight
    )

    this.relatedBooksList.setHasFixedSize(true)
    this.relatedBooksList.setItemViewCacheSize(32)
    this.relatedBooksList.layoutManager = LinearLayoutManager(this.context)
    (this.relatedBooksList.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
    this.relatedBooksList.addItemDecoration(
      CatalogFeedWithGroupsDecorator(this.screenInformation.dpToPixels(16).toInt())
    )

    this.feedWithGroupsAdapter =
      CatalogFeedWithGroupsAdapter(
        groups = this.feedWithGroupsData,
        coverLoader = this.bookCovers,
        onFeedSelected = this.viewModel::openFeed,
        onBookSelected = this.viewModel::openBookDetail
      )

    this.configureOPDSEntry(this.parameters.feedEntry)
  }

  override fun onDestroyView() {
    super.onDestroyView()
    this.thumbnailLoading?.cancel(true)
    this.thumbnailLoading = null
  }

  override fun onResume() {
    super.onResume()
    this.configureToolbar()
  }

  override fun onStart() {
    super.onStart()
    this.configureToolbar()
  }

  private fun configureToolbar() {
    val actionBar = this.supportActionBar ?: return
    actionBar.setDisplayHomeAsUpEnabled(true)
    actionBar.setHomeActionContentDescription(null)
    actionBar.show()
    this.toolbar.title = ""
    this.toolbar.setLogoOnClickListener {
      this.viewModel.goUpwards()
    }
    return
  }

//  private fun configurePreviewButton(previewStatus: BookPreviewStatus) {
//    val feedEntry = this.parameters.feedEntry
//    this.previewButton.apply {
//      isVisible = previewStatus != BookPreviewStatus.None
//      setText(
//        if (feedEntry.probableFormat == BookFormats.BookFormatDefinition.BOOK_FORMAT_AUDIO) {
//          R.string.catalogBookPreviewAudioBook
//        } else {
//          R.string.catalogBookPreviewBook
//        }
//      )
//      setOnClickListener {
//        viewModel.openBookPreview(this@CatalogBookDetailFragment.parameters.feedEntry)
//      }
//    }
//  }

  private fun configureOPDSEntry(feedEntry: FeedEntryOPDS) {
    val opds = feedEntry.feedEntry
    this.title.text = opds.title
    this.authors.text = opds.authorsCommaSeparated


    this.type.text = when (feedEntry.probableFormat) {
      BookFormats.BookFormatDefinition.BOOK_FORMAT_EPUB ->
        getString(R.string.catalogBookFormatEPUB)

      BookFormats.BookFormatDefinition.BOOK_FORMAT_AUDIO ->
        getString(R.string.catalogBookFormatAudioBook)

      BookFormats.BookFormatDefinition.BOOK_FORMAT_PDF ->
        getString(R.string.catalogBookFormatPDF)

      else -> {
        ""
      }
    }

    /*
     * Render the HTML present in the summary and insert it into the text view.
     */

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
      this.summary.text = Html.fromHtml(opds.summary, Html.FROM_HTML_MODE_LEGACY)
    } else {
      @Suppress("DEPRECATION")
      this.summary.text = Html.fromHtml(opds.summary)
    }

    this.summary.post {
      this.seeMore.visibility = if (this.summary.lineCount > 6) {
        this.summary.maxLines = 6
        this.seeMore.setOnClickListener {
          this.summary.maxLines = Integer.MAX_VALUE
          this.seeMore.visibility = View.GONE
        }
        View.VISIBLE
      } else {
        View.GONE
      }
    }

    this.configureMetadataTable(feedEntry.probableFormat, opds)

    /*
     * If there's a related feed, enable the "Related books..." item and open the feed
     * on demand.
     */

    val feedRelatedOpt = feedEntry.feedEntry.related
    if (feedRelatedOpt is Some<URI>) {
      val feedRelated = feedRelatedOpt.get()

      this.viewModel.relatedBooksFeedState.observe(
        this.viewLifecycleOwner,
        this::updateRelatedBooksUI
      )
      this.relatedBooksContainer.visibility = View.VISIBLE
      this.viewModel.loadRelatedBooks(feedRelated)
    } else {
      this.relatedBooksContainer.visibility = View.GONE
    }
  }

  private val genreUriScheme =
    "http://librarysimplified.org/terms/genres/Simplified/"

  private fun formatDuration(seconds: Double): String {
    val duration = Duration.standardSeconds(seconds.toLong())
    val hours = Duration.standardHours(duration.standardHours)
    val remaining = duration.minus(hours)

    return getString(
      R.string.catalogDurationFormat,
      hours.standardHours.toString(),
      remaining.standardMinutes.toString()
    )
  }

  private fun configureMetadataTable(
    probableFormat: BookFormats.BookFormatDefinition?,
    entry: OPDSAcquisitionFeedEntry
  ) {
    this.metadata.removeAllViews()

    val bookFormatText = when (probableFormat) {
      BookFormats.BookFormatDefinition.BOOK_FORMAT_EPUB ->
        getString(R.string.catalogBookFormatEPUB)
      BookFormats.BookFormatDefinition.BOOK_FORMAT_AUDIO ->
        getString(R.string.catalogBookFormatAudioBook)
      BookFormats.BookFormatDefinition.BOOK_FORMAT_PDF ->
        getString(R.string.catalogBookFormatPDF)
      else -> {
        ""
      }
    }

    if (bookFormatText.isNotEmpty()) {
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.getString(R.string.catalogMetaFormat)
      rowVal.text = bookFormatText
      this.metadata.addView(row)
    }

    val id = entry.id
    if(id.isNotEmpty()){
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.getString(R.string.catalogMetaId)
      if(id.startsWith("urn:isbn:")){
        rowVal.text = id.substring(9)
      }else{
        rowVal.text = id
      }

      this.metadata.addView(row)
    }

    val publishedOpt = entry.published
    if (publishedOpt is Some<DateTime>) {
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.getString(R.string.catalogMetaPublicationDate)
      val published = publishedOpt.get()
      if (published.dayOfMonth().get() == 1 && published.monthOfYear().get() == 1){
        rowVal.text = this.dateYearFormatter.print(published)
      }else{
        rowVal.text = this.dateFormatter.print(published)
      }
      this.metadata.addView(row)
    }

    val publisherOpt = entry.publisher
    if (publisherOpt is Some<String>) {
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.getString(R.string.catalogMetaPublisher)
      rowVal.text = publisherOpt.get()
      this.metadata.addView(row)
    }

    /*if (entry.distribution.isNotBlank()) {
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.getString(R.string.catalogMetaDistributor)
      rowVal.text = entry.distribution
      this.metadata.addView(row)
    }*/

    val languageOpt = entry.language
    if (languageOpt is Some<String>) {
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.getString(R.string.catalogMetaLanguage)
      rowVal.text = languageOpt.get()
      this.metadata.addView(row)
    }

    val categories =
      entry.categories.filter { opdsCategory -> opdsCategory.scheme == this.genreUriScheme }
    if (categories.isNotEmpty()) {
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.getString(R.string.catalogMetaCategories)
      rowVal.text = categories.joinToString(", ") { opdsCategory -> opdsCategory.effectiveLabel }
      this.metadata.addView(row)
    }

    val translators = entry.translators.filterNot { it.isBlank() }
    if (translators.isNotEmpty()) {
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.getString(R.string.catalogMetaTranslators)
      rowVal.text = translators.joinToString(", ")
      this.metadata.addView(row)
    }

    val narrators = entry.narrators.filterNot { it.isBlank() }
    if (narrators.isNotEmpty()) {
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.getString(R.string.catalogMetaNarrators)
      rowVal.text = narrators.joinToString(", ")
      this.metadata.addView(row)
    }

    val illustrators = entry.illustrators.filterNot { it.isBlank() }
    if (illustrators.isNotEmpty()) {
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.getString(R.string.catalogMetaIllustrators)
      rowVal.text = illustrators.joinToString(", ")
      this.metadata.addView(row)
    }

    /* Hide updated info for now, as it does not show what we want it to show
    this.run {
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.getString(R.string.catalogMetaUpdatedDate)
      rowVal.text = this.dateTimeFormatter.print(entry.updated)
      this.metadata.addView(row)
    }
     */

    val duration = entry.duration
    if (duration.isSome) {
      val durationValue = (duration as Some<Double>).get()
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.getString(R.string.catalogMetaDuration)
      rowVal.text = formatDuration(durationValue)
      this.metadata.addView(row)
    }

    val accessibilityFeatures = null //later entry.accessibilityFeatures
    if (accessibilityFeatures != null) {
      val accessibilityFeaturesValue = (accessibilityFeatures as Some<String>).get()
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.getString(R.string.catalogMetaAccessibilityFeatures)
      rowVal.text = "Value"
      this.metadata.addView(row)
    } else {
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.getString(R.string.catalogMetaAccessibilityFeatures)
      rowVal.text = this.getString(R.string.catalogMetaAccessibilityNotAvailable)
      this.metadata.addView(row)
    }

    val accessMode = null //later entry.accessMode
    if (accessMode != null) {
      val accessModeValue = (accessMode as Some<String>).get()
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.getString(R.string.catalogMetaAccessMode)
      rowVal.text = "Value"
      this.metadata.addView(row)
    } else {
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.getString(R.string.catalogMetaAccessMode)
      rowVal.text = this.getString(R.string.catalogMetaAccessibilityNotAvailable)
      this.metadata.addView(row)
    }

    val accessibilitySummary = null //later entry.accessibilitySummary
    if (accessibilitySummary != null) {
      val accessibilitySummaryValue = (accessibilitySummary as Some<String>).get()
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.getString(R.string.catalogMetaAccessibilitySummary)
      rowVal.text = "Value"
      this.metadata.addView(row)
    } else {
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.getString(R.string.catalogMetaAccessibilitySummary)
      rowVal.text = this.getString(R.string.catalogMetaAccessibilityNotAvailable)
      this.metadata.addView(row)
    }
  }

  private fun bookInfoViewOf(): Triple<View, TextView, TextView> {
    val row = this.layoutInflater.inflate(R.layout.book_detail_metadata_item, this.metadata, false)
    val rowKey = row.findViewById<TextView>(R.id.key)
    val rowVal = row.findViewById<TextView>(R.id.value)
    return Triple(row, rowKey, rowVal)
  }

  private fun reconfigureUI(book: BookWithStatus, bookPreviewStatus: BookPreviewStatus) {
    this.debugStatus.text = book.javaClass.simpleName

    //If there is a selected date, the book is selected
    if (book.book.entry.selected is Some<DateTime>) {
      //Set the drawable as the "checked" version
      this.selected.setImageDrawable(
        ContextCompat.getDrawable(this.requireContext(), R.drawable.baseline_check_circle_24)
      )
      //Add the audio description
      this.selected.contentDescription = getString(R.string.catalogAccessibilityBookUnselect)

      this.selected.setOnClickListener {
        //Set the button click to unselect the book
        this.viewModel.unselectBook(this.parameters.feedEntry)
    }
    }else {
      //Set the "unchecked" icon version
      this.selected.setImageDrawable(
        ContextCompat.getDrawable(this.requireContext(),R.drawable.round_add_circle_outline_24)
      )
      //Add audio description
      this.selected.contentDescription = getString(R.string.catalogAccessibilityBookSelect)
      this.selected.setOnClickListener {
        //Add book to selected
        this.viewModel.selectBook(this.parameters.feedEntry)
      }
    }
    when (val status = book.status) {
      is BookStatus.Held -> {
        this.onBookStatusHeld(status, bookPreviewStatus, book.book)
      }
      is BookStatus.Loaned -> {
        this.onBookStatusLoaned(status, book.book)
      }
      is BookStatus.Holdable -> {
        this.onBookStatusHoldable(status, bookPreviewStatus)
      }
      is BookStatus.Loanable -> {
        this.onBookStatusLoanable(status, bookPreviewStatus)
      }
      is BookStatus.RequestingLoan -> {
        this.onBookStatusRequestingLoan()
      }
      is BookStatus.Revoked -> {
        this.onBookStatusRevoked(status)
      }
      is BookStatus.FailedLoan -> {
        this.onBookStatusFailedLoan(status)
      }
      is BookStatus.ReachedLoanLimit -> {
        this.onBookStatusReachedLoanLimit()
      }
      is BookStatus.FailedRevoke -> {
        this.onBookStatusFailedRevoke(status)
      }
      is BookStatus.FailedDownload -> {
        this.onBookStatusFailedDownload(status)
      }
      is BookStatus.RequestingRevoke -> {
        this.onBookStatusRequestingRevoke()
      }
      is BookStatus.RequestingDownload -> {
        this.onBookStatusRequestingDownload()
      }
      is BookStatus.Downloading -> {
        this.onBookStatusDownloading(status)
      }
      is BookStatus.DownloadWaitingForExternalAuthentication -> {
        this.onBookStatusDownloadWaitingForExternalAuthentication()
      }
      is BookStatus.DownloadExternalAuthenticationInProgress -> {
        this.onBookStatusDownloadExternalAuthenticationInProgress()
      }
      is BookStatus.Selected -> {
        this.onBookStatusBookSelected(book.book.id,book.book.entry.title, status)
      }
      is BookStatus.Unselected -> {
        this.onBookStatusBookUnselected(book.book.id, book.book.entry.title,status)
      }
    }
  }

  private fun updateRelatedBooksUI(feedState: CatalogFeedState?) {
    when (feedState) {
      is CatalogFeedState.CatalogFeedLoading -> {
        this.onCatalogFeedLoading()
      }
      is CatalogFeedState.CatalogFeedLoaded.CatalogFeedWithGroups -> {
        this.onCatalogFeedWithGroups(feedState)
      }
      is CatalogFeedState.CatalogFeedLoaded.CatalogFeedWithoutGroups -> {
        this.onCatalogFeedWithoutGroups(feedState)
      }
      else -> {
        this.relatedBooksContainer.visibility = View.GONE
      }
    }
  }

  private fun onCatalogFeedLoading() {
    this.relatedBooksContainer.visibility = View.VISIBLE
    this.relatedBooksList.visibility = View.INVISIBLE
    this.relatedBooksLoading.visibility = View.VISIBLE
  }

  private fun onCatalogFeedWithoutGroups(
    feedState: CatalogFeedState.CatalogFeedLoaded.CatalogFeedWithoutGroups
  ) {
    this.relatedBooksContainer.visibility = View.VISIBLE
    this.relatedBooksLoading.visibility = View.INVISIBLE
    this.relatedBooksList.visibility = View.VISIBLE

    this.feedWithoutGroupsAdapter =
      CatalogPagedAdapter(
        context = requireActivity(),
        listener = this.viewModel,
        buttonCreator = this.buttonCreator,
        bookCovers = this.bookCovers,
        profilesController = this.profilesController
      )

    this.relatedBooksList.adapter = this.feedWithoutGroupsAdapter
    feedState.entries.observe(this) { newPagedList ->
      this.logger.debug("received paged list ({} elements)", newPagedList.size)
      this.feedWithoutGroupsAdapter.submitList(newPagedList)
    }
  }

  private fun onCatalogFeedWithGroups(
    feedState: CatalogFeedState.CatalogFeedLoaded.CatalogFeedWithGroups
  ) {
    this.relatedBooksContainer.visibility = View.VISIBLE
    this.relatedBooksLoading.visibility = View.INVISIBLE
    this.relatedBooksList.visibility = View.VISIBLE

    this.relatedBooksList.adapter = this.feedWithGroupsAdapter
    this.feedWithGroupsData.clear()
    this.feedWithGroupsData.addAll(feedState.feed.feedGroupsInOrder)
    this.feedWithGroupsAdapter.notifyDataSetChanged()
  }

  private fun onBookStatusFailedLoan(
    bookStatus: BookStatus.FailedLoan,
  ) {
    //If error is 401 or 400, it's a response to the tried and failed token refresh
    //So they get special treatment
    if (bookStatus.message.contains("401") || bookStatus.message.contains("400")) {
      logger.debug("session expired, trigger login popup")
      //show popup if there already isn't one showing
      if (!popUpShown) {
        onLogInNeeded()
      }
    }

    this.buttons.removeAllViews()
    this.buttons.addView(
      this.buttonCreator.createDismissButton {
        this.viewModel.dismissBorrowError()
      }
    )
    this.buttons.addView(this.buttonCreator.createButtonSpace())
    this.buttons.addView(
      this.buttonCreator.createDetailsButton {
        this.viewModel.showError(bookStatus.result)
      }
    )
    this.buttons.addView(this.buttonCreator.createButtonSpace())
    this.buttons.addView(
      this.buttonCreator.createRetryButton {
        this.viewModel.borrowMaybeAuthenticated()
      }
    )
    this.checkButtonViewCount()

    this.statusInProgress.visibility = View.INVISIBLE
    this.statusIdle.visibility = View.INVISIBLE
    this.statusFailed.visibility = View.VISIBLE
    this.statusFailedText.text = this.resources.getText(R.string.catalogOperationFailed)
  }

  /**
   * Show a popup informing user of token expiration and open up the login page when dismissed.
   */
  private fun onLogInNeeded() {
    logger.debug("Showing 'Please login' popup")
    //Ensure only one popup is shown at a time
    popUpShown = true
    val builder: AlertDialog.Builder = AlertDialog.Builder(this.requireContext())
    builder
      .setMessage(R.string.bookSessionExpiredMessage)
      .setTitle(R.string.bookSessionExpiredTitle)
      .setPositiveButton(R.string.bookSessionExpiredButton) { dialog, which ->
        this.viewModel.openLoginDialog(parameters.feedEntry.accountID)
        popUpShown = false
        dialog.dismiss()
      }

    val dialog: AlertDialog = builder.create()
    dialog.show()

  }

  private fun onBookStatusRevoked(
    bookStatus: BookStatus.Revoked
  ) {
    this.buttons.removeAllViews()
    this.buttons.addView(this.buttonCreator.createCenteredTextForButtons(R.string.catalogRequesting))
    this.checkButtonViewCount()

    this.statusInProgress.visibility = View.INVISIBLE
    this.statusIdle.visibility = View.VISIBLE
    this.statusFailed.visibility = View.INVISIBLE

    this.statusIdleText.text =
      CatalogBookAvailabilityStrings.statusString(this.resources, bookStatus)
  }

  private fun onBookStatusRequestingLoan() {
    this.buttons.removeAllViews()
    this.buttons.addView(this.buttonCreator.createCenteredTextForButtons(R.string.catalogRequesting))
    this.checkButtonViewCount()

    this.statusInProgress.visibility = View.VISIBLE
    this.statusIdle.visibility = View.INVISIBLE
    this.statusFailed.visibility = View.INVISIBLE

    this.statusInProgressText.visibility = View.GONE
    this.statusInProgressBar.isIndeterminate = true
  }

  private fun onBookStatusLoanable(
    bookStatus: BookStatus.Loanable,
    bookPreviewStatus: BookPreviewStatus
  ) {
    this.buttons.removeAllViews()

    // Do not createPreviewButton button, regarless of statuses
    //val createPreviewButton = bookPreviewStatus != BookPreviewStatus.None
    val createPreviewButton = false
    
//    if (createPreviewButton) {
//      this.buttons.addView(this.buttonCreator.createButtonSpace())
//    } else {
//      this.buttons.addView(this.buttonCreator.createButtonSizedSpace())
//    }

    this.buttons.addView(
      this.buttonCreator.createGetButton(
        onClick = {
          this.viewModel.borrowMaybeAuthenticated()
        }
      )
    )

    if (createPreviewButton) {
      this.buttons.addView(this.buttonCreator.createButtonSpace())
      this.buttons.addView(
        this.buttonCreator.createReadPreviewButton(
          bookFormat = parameters.feedEntry.probableFormat,
          onClick = {
            viewModel.openBookPreview(parameters.feedEntry)
          }
        )
      )
    } else {
      this.buttons.addView(this.buttonCreator.createButtonSizedSpace())
    }
    this.checkButtonViewCount()

    this.statusInProgress.visibility = View.INVISIBLE
    this.statusIdle.visibility = View.VISIBLE
    this.statusFailed.visibility = View.INVISIBLE

    this.statusIdleText.text =
      CatalogBookAvailabilityStrings.statusString(this.resources, bookStatus)
  }

  private fun onBookStatusReachedLoanLimit() {
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.bookReachedLoanLimitDialogTitle)
      .setMessage(R.string.bookReachedLoanLimitDialogMessage)
      .setPositiveButton(R.string.bookReachedLoanLimitDialogButton) { dialog, _ ->
        dialog.dismiss()
      }
      .create()
      .show()

    viewModel.resetInitialBookStatus(this.parameters.feedEntry)
  }

  /**
   * Show a toast for successful select and trigger status reset
   */
  private fun onBookStatusBookSelected(bookID: BookID, title: String, status: BookStatus) {
    Toast.makeText(this.requireContext(), getString(R.string.catalogBookSelect, title), Toast.LENGTH_SHORT).show()
    viewModel.resetPreviousBookStatus(bookID, status, true)
    logger.debug("BookStatusReset")
  }

  /**
   * Show a toast for successful unselect and trigger status reset
   */
  private fun onBookStatusBookUnselected(bookID: BookID,  title: String, status: BookStatus) {
    Toast.makeText(this.requireContext(), getString(R.string.catalogBookUnselect, title), Toast.LENGTH_SHORT).show()
    viewModel.resetPreviousBookStatus(bookID, status, false)
    logger.debug("BookStatusResetToPrevious")
  }
  private fun onBookStatusHoldable(
    bookStatus: BookStatus.Holdable,
    bookPreviewStatus: BookPreviewStatus
  ) {
    this.buttons.removeAllViews()

    this.buttons.addView(
      this.buttonCreator.createReserveButton(
        onClick = {
          this.viewModel.reserveMaybeAuthenticated()
        }
      )
    )
    this.buttons.addView(this.buttonCreator.createButtonSizedSpace())

    this.checkButtonViewCount()

    this.statusInProgress.visibility = View.INVISIBLE
    this.statusIdle.visibility = View.VISIBLE
    this.statusFailed.visibility = View.INVISIBLE

    this.statusIdleText.text =
      CatalogBookAvailabilityStrings.statusString(this.resources, bookStatus)
  }

  private fun onBookStatusHeld(
    bookStatus: BookStatus.Held,
    bookPreviewStatus: BookPreviewStatus,
    book: Book
  ) {
    this.buttons.removeAllViews()
    
    //val createPreviewButton = bookPreviewStatus != BookPreviewStatus.None
    val createPreviewButton = false

    when (bookStatus) {
      is BookStatus.Held.HeldInQueue -> {
        if (createPreviewButton) {
          this.buttons.addView(
            this.buttonCreator.createReadPreviewButton(
              bookFormat = parameters.feedEntry.probableFormat,
              onClick = {
                viewModel.openBookPreview(parameters.feedEntry)
              }
            )
          )
          this.buttons.addView(this.buttonCreator.createButtonSpace())
        }

        if (bookStatus.isRevocable) {
          this.buttons.addView(
            this.buttonCreator.createRevokeHoldButton(
              onClick = {
                this.revokeHoldPopup(book)
              }
            )
          )
        } else {
          this.buttons.addView(
            this.buttonCreator.createCenteredTextForButtons(R.string.catalogHoldCannotCancel)
          )
        }

        //If there is no preview button, create a dummy
        if (!createPreviewButton) {
          this.buttons.addView(
            this.buttonCreator.createButtonSizedSpace()
          )
        }
      }

      is BookStatus.Held.HeldReady -> {
        this.buttons.addView(
          this.buttonCreator.createGetButton(
            onClick = {
              this.viewModel.borrowMaybeAuthenticated()
            }
          )
        )

        if (createPreviewButton) {
          this.buttons.addView(this.buttonCreator.createButtonSpace())

          this.buttons.addView(
            this.buttonCreator.createReadPreviewButton(
              bookFormat = parameters.feedEntry.probableFormat,
              onClick = {
                viewModel.openBookPreview(parameters.feedEntry)
              }
            )
          )
        }

        if (bookStatus.isRevocable) {
          this.buttons.addView(this.buttonCreator.createButtonSpace())

          this.buttons.addView(
            this.buttonCreator.createRevokeHoldButton(
              onClick = {
                this.revokeHoldPopup(book)
              }
            )
          )
        } else if (!createPreviewButton) {
          // if the book is not revocable and there's no preview button, we need to add a dummy
          // button on the right
          this.buttons.addView(this.buttonCreator.createButtonSizedSpace())
        }
      }
    }
    this.checkButtonViewCount()

    this.statusInProgress.visibility = View.INVISIBLE
    this.statusIdle.visibility = View.VISIBLE
    this.statusFailed.visibility = View.INVISIBLE
    this.statusIdleText.text =
      CatalogBookAvailabilityStrings.statusString(this.resources, bookStatus)
  }

  private fun onBookStatusLoaned(
    bookStatus: BookStatus.Loaned,
    book: Book
  ) {
    this.buttons.removeAllViews()

    when (bookStatus) {
      is BookStatus.Loaned.LoanedNotDownloaded -> {
        this.buttons.addView(
          if (bookStatus.isOpenAccess) {
            this.buttonCreator.createGetButton(
              onClick = {
                this.viewModel.borrowMaybeAuthenticated()
              }
            )
          } else {
            this.buttonCreator.createDownloadButton(
              onClick = {
                this.viewModel.borrowMaybeAuthenticated()
              }
            )
          }
        )
      }
      is BookStatus.Loaned.LoanedDownloaded -> {
        when (val format = book.findPreferredFormat()) {
          is BookFormat.BookFormatPDF,
          is BookFormat.BookFormatEPUB -> {
            this.buttons.addView(
              this.buttonCreator.createReadButton(
                onClick = {
                  this.viewModel.openViewer(format)
                }
              )
            )
          }
          is BookFormat.BookFormatAudioBook -> {
            this.buttons.addView(
              this.buttonCreator.createListenButton(
                onClick = {
                  this.viewModel.openViewer(format)
                }
              )
            )
          }
          else -> {
            // do nothing
          }
        }
      }
    }

    val isRevocable = this.viewModel.bookCanBeRevoked && this.buildConfig.allowReturns()
    if (isRevocable) {
      this.buttons.addView(this.buttonCreator.createButtonSpace())
      this.buttons.addView(
        this.buttonCreator.createRevokeLoanButton(
          onClick = {
            this.revokeLoanPopup(book)
          }
        )
      )
    } else if (this.viewModel.bookCanBeDeleted) {
      this.buttons.addView(this.buttonCreator.createButtonSpace())
      this.buttons.addView(
        this.buttonCreator.createRevokeLoanButton(
          onClick = {
            this.viewModel.delete()
          }
        )
      )
    } else {
      this.buttons.addView(this.buttonCreator.createButtonSizedSpace())
    }

    this.checkButtonViewCount()

    this.statusInProgress.visibility = View.INVISIBLE
    this.statusIdle.visibility = View.VISIBLE
    this.statusFailed.visibility = View.INVISIBLE
    this.statusIdleText.text =
      CatalogBookAvailabilityStrings.statusString(this.resources, bookStatus)
  }

  private fun onBookStatusDownloading(
    bookStatus: BookStatus.Downloading,
  ) {
    /*
     * XXX: https://jira.nypl.org/browse/SIMPLY-3444
     *
     * Avoid creating a cancel button until we can reliably support cancellation for *all* books.
     * That is, when the Adobe DRM is dead and buried.
     */

    this.buttons.removeAllViews()
    this.buttons.addView(
      this.buttonCreator.createCancelDownloadButton(
        onClick = {
          this.viewModel.cancelDownload()
        }
      )
    )
    this.buttons.addView(this.buttonCreator.createButtonSizedSpace())

    this.statusInProgress.visibility = View.VISIBLE
    this.statusIdle.visibility = View.INVISIBLE
    this.statusFailed.visibility = View.INVISIBLE

    val progressPercent = bookStatus.progressPercent?.toInt()
    if (progressPercent != null) {
      this.statusInProgressText.visibility = View.VISIBLE
      this.statusInProgressText.text = "$progressPercent%"
      this.statusInProgressBar.isIndeterminate = false
      this.statusInProgressBar.progress = progressPercent
    } else {
      this.statusInProgressText.visibility = View.GONE
      this.statusInProgressBar.isIndeterminate = true
      this.buttons.addView(this.buttonCreator.createCenteredTextForButtons(R.string.catalogDownloading))
      this.checkButtonViewCount()
    }
    //Check file size, and show popup if file is too big

    //Check the size expected from one of the first packets
    //bookStatus.currentTotalBytes == 0L does not work, sometimes skips
    if (bookStatus.currentTotalBytes!! < 10000L) {
      //Expected size of the book that is downloading
      val expectedSize = bookStatus.expectedTotalBytes
      //How much space is free on the device
      val freeSpace = getInternalMem()
      this.logger.debug("Assumed size of file: {}", formatSize(expectedSize))

      //Null check the expected size
      if (expectedSize != null) {
        //If size smaller than internal memory, it should technically fit to memory
        if (expectedSize < freeSpace) {
          logger.debug("Enough space for download")
          logger.debug(
            "Remaining space: {}",
            formatSize(freeSpace - expectedSize)
          )
        } else {
          logger.debug("Not enough space for download")
          //To avoid showing multiples of the same popup on top of one another,
          // show only if there currently is no popup showing
          if (!popUpShown) {
            //Inform user with a popup when file doesn't fit
            onFileTooBigToStore(freeSpace, expectedSize - freeSpace)
            //Cancel the download
            this.viewModel.cancelDownload()
          }
        }
      }
    }
  }

  //A boolean lock used for showing only one copy of a popup at a time
  private var popUpShown = false

  /**
   * Returns the amount of free internal memory there is on the device.
   */
  private fun getInternalMem() : Long {
    // Fetching internal memory information
    val iPath: File = Environment.getDataDirectory()
    val iStat = StatFs(iPath.path)
    val iBlockSize = iStat.blockSizeLong
    val iAvailableBlocks = iStat.availableBlocksLong

    //Count and return the available internal memory
    return iAvailableBlocks * iBlockSize
  }

  /**
   * Change the bit presentation of a value to
   * a better understandable form.
   * Returns a string with the size suffix added.
   */
  private fun formatSize(number : Long?) : String {
    //Get the value that needs formatting
    var expSize: Long = number?: 0L
    //Create a variable that is made into the suffix
    var suffix: String? = null
    //Format the expSize to readable form, either kilo or megabytes
    if (expSize >= 1024) {
      suffix = "KB"
      expSize /= 1024
      if (expSize >= 1024) {
        suffix = "MB"
        expSize /= 1024
      }
    }
    //Make the long value into a string
    val expSizeString = StringBuilder(expSize.toString())
    //If there is a suffix, add it to the end of the expSize
    if (suffix != null) {
      expSizeString.append(suffix)
    }

    //Return a string form of the formatted variable
    return expSizeString.toString()
  }

  /**
   * If there is no space for the book on the device, show a popup that informs the user about the
   * required space.
   */
  private fun onFileTooBigToStore(deviceSpace: Long, neededSpace : Long) {
    //Mark that a popup is currently shown
    popUpShown = true
    logger.debug("Showing 'Not enough space' popup")
    val builder: AlertDialog.Builder = AlertDialog.Builder(this.requireContext())
    builder
      .setMessage(getString(
        R.string.bookNotEnoughSpaceMessage,
        formatSize(deviceSpace),
        formatSize(neededSpace)))
      .setTitle(R.string.bookNotEnoughSpaceTitle)
      .setPositiveButton(R.string.bookNotEnoughSpaceButton) { dialog, which ->
        //Set the popup as closed
        popUpShown = false
      }

    val dialog: AlertDialog = builder.create()
    dialog.show()

  }
  /**
   * Show user a popup requiring user to confirm a loan revoke
   */
  private fun revokeLoanPopup(book: Book) {
    //Mark that a popup is currently shown
    popUpShown = true
    logger.debug("Showing loan revoke popup")
    val builder: AlertDialog.Builder = AlertDialog.Builder(this.requireContext())
    builder
      .setTitle(getString(R.string.bookConfirmReturnTitle, book.entry.title))
      .setMessage(R.string.bookConfirmReturnMessage)
      .setPositiveButton(R.string.bookConfirmReturnConfirmButton) { dialog, which ->
        //Set the popup as closed
        //And start revoke
        this.viewModel.revokeMaybeAuthenticated()
        popUpShown = false
      }
      .setNeutralButton(R.string.bookConfirmReturnCancelButton) { dialog, which ->
        //Do nothing, don't revoke the book
        popUpShown = false
      }

    val dialog: AlertDialog = builder.create()
    dialog.show()
  }

  /**
   * Show user a popup requiring user to confirm a hold revoke
   */
  private fun revokeHoldPopup(book: Book) {
    //Mark that a popup is currently shown
    popUpShown = true
    logger.debug("Showing revoke hold popup")
    val builder: AlertDialog.Builder = AlertDialog.Builder(this.requireContext())
    builder
      .setTitle(getString(R.string.bookConfirmRevokeTitle, book.entry.title))
      .setMessage(R.string.bookConfirmRevokeMessage)
      .setPositiveButton(R.string.bookConfirmRevokeConfirmButton) { dialog, which ->
        //Set the popup as closed
        //And start revoke
        this.viewModel.revokeMaybeAuthenticated()
        popUpShown = false
      }
      .setNeutralButton(R.string.bookConfirmReturnCancelButton) { dialog, which ->
        //Do nothing, don't revoke the book
        popUpShown = false
      }

    val dialog: AlertDialog = builder.create()
    dialog.show()
  }

  private fun onBookStatusDownloadWaitingForExternalAuthentication() {
    this.buttons.removeAllViews()
    this.buttons.addView(this.buttonCreator.createCenteredTextForButtons(R.string.catalogLoginRequired))
    this.checkButtonViewCount()

    this.statusInProgress.visibility = View.VISIBLE
    this.statusIdle.visibility = View.INVISIBLE
    this.statusFailed.visibility = View.INVISIBLE
    this.statusInProgressText.visibility = View.GONE
    this.statusInProgressBar.isIndeterminate = true
  }

  private fun onBookStatusDownloadExternalAuthenticationInProgress() {
    this.buttons.removeAllViews()
    this.buttons.addView(this.buttonCreator.createCenteredTextForButtons(R.string.catalogLoginRequired))
    this.checkButtonViewCount()

    this.statusInProgress.visibility = View.VISIBLE
    this.statusIdle.visibility = View.INVISIBLE
    this.statusFailed.visibility = View.INVISIBLE
    this.statusInProgressText.visibility = View.GONE
    this.statusInProgressBar.isIndeterminate = true
  }

  private fun onBookStatusRequestingDownload() {
    this.buttons.removeAllViews()
    this.buttons.addView(this.buttonCreator.createCenteredTextForButtons(R.string.catalogRequesting))
    this.checkButtonViewCount()

    this.statusInProgress.visibility = View.VISIBLE
    this.statusIdle.visibility = View.INVISIBLE
    this.statusFailed.visibility = View.INVISIBLE
    this.statusInProgressText.visibility = View.GONE
    this.statusInProgressBar.isIndeterminate = true
  }

  private fun onBookStatusRequestingRevoke() {
    this.buttons.removeAllViews()
    this.buttons.addView(this.buttonCreator.createCenteredTextForButtons(R.string.catalogRequesting))
    this.checkButtonViewCount()

    this.statusInProgress.visibility = View.VISIBLE
    this.statusIdle.visibility = View.INVISIBLE
    this.statusFailed.visibility = View.INVISIBLE
    this.statusInProgressText.visibility = View.GONE
    this.statusInProgressBar.isIndeterminate = true
  }

  private fun onBookStatusFailedDownload(
    bookStatus: BookStatus.FailedDownload,
  ) {
    this.buttons.removeAllViews()
    this.buttons.addView(
      this.buttonCreator.createDismissButton {
        this.viewModel.dismissBorrowError()
      }
    )
    this.buttons.addView(this.buttonCreator.createButtonSpace())
    this.buttons.addView(
      this.buttonCreator.createDetailsButton {
        this.viewModel.showError(bookStatus.result)
      }
    )
    this.buttons.addView(this.buttonCreator.createButtonSpace())
    this.buttons.addView(
      this.buttonCreator.createRetryButton {
        this.viewModel.borrowMaybeAuthenticated()
      }
    )
    this.checkButtonViewCount()

    this.statusInProgress.visibility = View.INVISIBLE
    this.statusIdle.visibility = View.INVISIBLE
    this.statusFailed.visibility = View.VISIBLE
    this.statusFailedText.text = this.getString(R.string.catalogOperationFailed)
  }

  private fun onBookStatusFailedRevoke(
    bookStatus: BookStatus.FailedRevoke,
  ) {
    this.buttons.removeAllViews()
    this.buttons.addView(
      this.buttonCreator.createDismissButton {
        this.viewModel.dismissRevokeError()
      }
    )
    this.buttons.addView(this.buttonCreator.createButtonSpace())
    this.buttons.addView(
      this.buttonCreator.createDetailsButton {
        this.viewModel.showError(bookStatus.result)
      }
    )
    this.buttons.addView(this.buttonCreator.createButtonSpace())
    this.buttons.addView(
      this.buttonCreator.createRetryButton {
        this.viewModel.revokeMaybeAuthenticated()
      }
    )
    this.checkButtonViewCount()

    this.statusInProgress.visibility = View.INVISIBLE
    this.statusIdle.visibility = View.INVISIBLE
    this.statusFailed.visibility = View.VISIBLE
    this.statusFailedText.text = this.resources.getText(R.string.catalogOperationFailed)
  }

  private fun checkButtonViewCount() {
    Preconditions.checkState(
      this.buttons.childCount > 0,
      "At least one button must be present (existing ${this.buttons.childCount})"
    )
  }
}
