package org.librarysimplified.ui.catalog

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.recyclerview.widget.RecyclerView
import com.google.common.base.Preconditions
import com.google.common.util.concurrent.FluentFuture
import com.io7m.jfunctional.Some
import org.joda.time.DateTime
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.book_database.api.BookFormats
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookWithStatus
import org.nypl.simplified.books.covers.BookCoverProviderType
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedEntry.FeedEntryCorrupt
import org.nypl.simplified.feeds.api.FeedEntry.FeedEntryOPDS
import org.nypl.simplified.futures.FluentFutureExtensions.map
import org.nypl.simplified.opds.core.OPDSAvailabilityHeld
import org.nypl.simplified.opds.core.OPDSAvailabilityHeldReady
import org.nypl.simplified.opds.core.OPDSAvailabilityHoldable
import org.nypl.simplified.opds.core.OPDSAvailabilityLoanable
import org.nypl.simplified.opds.core.OPDSAvailabilityLoaned
import org.nypl.simplified.opds.core.OPDSAvailabilityMatcherType
import org.nypl.simplified.opds.core.OPDSAvailabilityOpenAccess
import org.nypl.simplified.opds.core.OPDSAvailabilityRevoked
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.slf4j.LoggerFactory
import java.io.File

/**
 * A view holder for a single cell in an infinitely-scrolling feed.
 */

class CatalogPagedViewHolder(
  private val context: Context,
  private val listener: CatalogPagedViewListener,
  private val parent: View,
  private val buttonCreator: CatalogButtons,
  private val bookCovers: BookCoverProviderType,
  private val profilesController: ProfilesControllerType
) : RecyclerView.ViewHolder(parent) {

  private val logger = LoggerFactory.getLogger(CatalogPagedViewHolder::class.java)

  private var thumbnailLoading: FluentFuture<Unit>? = null

  private val idle =
    this.parent.findViewById<ViewGroup>(R.id.bookCellIdle)!!
  private val corrupt =
    this.parent.findViewById<ViewGroup>(R.id.bookCellCorrupt)!!
  private val error =
    this.parent.findViewById<ViewGroup>(R.id.bookCellError)!!
  private val progress =
    this.parent.findViewById<ViewGroup>(R.id.bookCellInProgress)!!

  private val idleCover =
    this.parent.findViewById<ImageView>(R.id.bookCellIdleCover)!!
  private val idleProgress =
    this.parent.findViewById<ProgressBar>(R.id.bookCellIdleCoverProgress)!!
  private val idleTitle =
    this.idle.findViewById<TextView>(R.id.bookCellIdleTitle)!!
  private val idleMeta =
    this.idle.findViewById<TextView>(R.id.bookCellIdleMeta)!!
  private val idleLoanTime =
    this.idle.findViewById<TextView>(R.id.bookCellIdleLoanTime)!!
  private val idleAuthor =
    this.idle.findViewById<TextView>(R.id.bookCellIdleAuthor)!!
  private val idleButtons =
    this.idle.findViewById<ViewGroup>(R.id.bookCellIdleButtons)!!
  private val idleSelectedButton =
    this.idle.findViewById<ImageView>(R.id.bookCellIdleSelect)!!

  private val progressProgress =
    this.parent.findViewById<ProgressBar>(R.id.bookCellInProgressBar)!!
  private val progressText =
    this.parent.findViewById<TextView>(R.id.bookCellInProgressTitle)!!

  private val errorTitle =
    this.error.findViewById<TextView>(R.id.bookCellErrorTitle)
  private val errorDismiss =
    this.error.findViewById<Button>(R.id.bookCellErrorButtonDismiss)
  private val errorDetails =
    this.error.findViewById<Button>(R.id.bookCellErrorButtonDetails)
  private val errorRetry =
    this.error.findViewById<Button>(R.id.bookCellErrorButtonRetry)

  private var feedEntry: FeedEntry? = null

  fun bindTo(item: FeedEntry?) {
    this.feedEntry = item

    return when (item) {
      is FeedEntryCorrupt -> {
        this.setVisibilityIfNecessary(this.corrupt, View.VISIBLE)
        this.checkSomethingIsVisible()
      }

      is FeedEntryOPDS -> {
        this.listener.registerObserver(item, this::onBookChanged)
        this.checkSomethingIsVisible()
      }

      null -> {
        this.unbind()
        this.setVisibilityIfNecessary(this.idle, View.VISIBLE)
        this.setVisibilityIfNecessary(this.idleProgress, View.VISIBLE)
        this.checkSomethingIsVisible()
      }
    }
  }

  private fun setVisibilityIfNecessary(
    view: View,
    visibility: Int
  ) {
    if (view.visibility != visibility) {
      view.visibility = visibility
    }
  }

  private fun onFeedEntryOPDS(item: FeedEntryOPDS, book: Book) {
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.GONE)
    this.setVisibilityIfNecessary(this.idle, View.VISIBLE)
    this.setVisibilityIfNecessary(this.progress, View.GONE)

    this.setVisibilityIfNecessary(this.idleCover, View.INVISIBLE)
    this.idleCover.setImageDrawable(null)

    this.setVisibilityIfNecessary(this.idleLoanTime, View.INVISIBLE)

    this.setVisibilityIfNecessary(this.idleProgress, View.VISIBLE)
    this.idleTitle.text = item.feedEntry.title
    this.idleAuthor.text = item.feedEntry.authorsCommaSeparated
    this.errorTitle.text = item.feedEntry.title

    this.idleMeta.text = when (item.probableFormat) {
      BookFormats.BookFormatDefinition.BOOK_FORMAT_EPUB ->
        context.getString(R.string.catalogBookFormatEPUB)
      BookFormats.BookFormatDefinition.BOOK_FORMAT_AUDIO ->
        context.getString(R.string.catalogBookFormatAudioBook)
      BookFormats.BookFormatDefinition.BOOK_FORMAT_PDF ->
        context.getString(R.string.catalogBookFormatPDF)
      null -> ""
    }
    //If there is a selected date, the book is selected
    if (book.entry.selected is Some<DateTime>) {
      //Set the drawable as the "checked" version
      this.idleSelectedButton.setImageDrawable(
        ContextCompat.getDrawable(context,R.drawable.baseline_check_circle_24)
      )
      //Set audio description to the button
      this.idleSelectedButton.contentDescription = context.getString(R.string.catalogAccessibilityBookUnselect)
      this.idleSelectedButton.setOnClickListener{
        //Remove book from selected
        this.listener.unselectBook(item)
      }
    } else {
      //Set the "unchecked" icon version
      this.idleSelectedButton.setImageDrawable(
        ContextCompat.getDrawable(context,R.drawable.round_add_circle_outline_24)
      )
      //Add audio description
      this.idleSelectedButton.contentDescription = context.getString(R.string.catalogAccessibilityBookSelect)
      this.idleSelectedButton.setOnClickListener {
        //Add book to selected
        this.listener.selectBook(item)
      }
    }

    val targetHeight =
      this.parent.resources.getDimensionPixelSize(
        org.librarysimplified.books.covers.R.dimen.cover_thumbnail_height
      )
    val targetWidth = 0
    this.thumbnailLoading =
      this.bookCovers.loadThumbnailInto(
        item, this.idleCover, targetWidth, targetHeight, true
      ).map {
        this.setVisibilityIfNecessary(this.idleProgress, View.INVISIBLE)
        this.setVisibilityIfNecessary(this.idleCover, View.VISIBLE)
      }

    val onClick: (View) -> Unit = { this.listener.openBookDetail(item) }
    this.idle.setOnClickListener(onClick)
  }

  private fun onBookChanged(bookWithStatus: BookWithStatus) {
    this.onFeedEntryOPDS(this.feedEntry as FeedEntryOPDS, bookWithStatus.book)
    this.onBookWithStatus(bookWithStatus)
    this.checkSomethingIsVisible()
  }

  private fun checkSomethingIsVisible() {
    Preconditions.checkState(
      this.idle.visibility == View.VISIBLE ||
        this.progress.visibility == View.VISIBLE ||
        this.corrupt.visibility == View.VISIBLE ||
        this.error.visibility == View.VISIBLE,
      "Something must be visible!"
    )
  }

  private fun onBookWithStatus(book: BookWithStatus) {
    return when (val status = book.status) {
      is BookStatus.Held.HeldInQueue ->
        this.onBookStatusHeldInQueue(status, book.book)
      is BookStatus.Held.HeldReady ->
        this.onBookStatusHeldReady(status, book.book)
      is BookStatus.Holdable ->
        this.onBookStatusHoldable(book.book)
      is BookStatus.Loanable ->
        this.onBookStatusLoanable(book.book)
      is BookStatus.Loaned.LoanedNotDownloaded ->
        this.onBookStatusLoanedNotDownloaded(status, book.book)
      is BookStatus.Loaned.LoanedDownloaded ->
        this.onBookStatusLoanedDownloaded(status, book.book)
      is BookStatus.Revoked ->
        this.onBookStatusRevoked(book)
      is BookStatus.ReachedLoanLimit ->
        this.onBookStatusReachedLoanLimit()
      is BookStatus.FailedRevoke ->
        this.onBookStatusFailedRevoke(status, book.book)
      is BookStatus.FailedDownload ->
        this.onBookStatusFailedDownload(status, book.book)
      is BookStatus.FailedLoan ->
        this.onBookStatusFailedLoan(status, book.book)

      is BookStatus.RequestingRevoke,
      is BookStatus.RequestingLoan,
      is BookStatus.RequestingDownload -> {
        this.setVisibilityIfNecessary(this.corrupt, View.GONE)
        this.setVisibilityIfNecessary(this.error, View.GONE)
        this.setVisibilityIfNecessary(this.idle, View.GONE)
        this.setVisibilityIfNecessary(this.progress, View.VISIBLE)

        this.progressText.text = book.book.entry.title
        this.progressProgress.isIndeterminate = true
      }

      is BookStatus.Downloading ->
        this.onBookStatusDownloading(book, status)
      is BookStatus.DownloadWaitingForExternalAuthentication ->
        this.onBookStatusDownloadWaitingForExternalAuthentication(book.book)
      is BookStatus.DownloadExternalAuthenticationInProgress ->
        this.onBookStatusDownloadExternalAuthenticationInProgress(book.book)
      is BookStatus.Selected -> this.onBookStatusSelected(book)
      is BookStatus.Unselected -> this.onBookStatusUnselected(book)
    }
  }

  private fun onBookStatusFailedRevoke(
    bookStatus: BookStatus.FailedRevoke,
    book: Book
  ) {
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.VISIBLE)
    this.setVisibilityIfNecessary(this.idle, View.GONE)
    this.setVisibilityIfNecessary(this.progress, View.GONE)

    this.errorDismiss.setOnClickListener {
      this.listener.dismissRevokeError(this.feedEntry as FeedEntryOPDS)
    }
    this.errorDetails.setOnClickListener {
      this.listener.showTaskError(book, bookStatus.result)
    }
    this.errorRetry.setOnClickListener {
      this.listener.revokeMaybeAuthenticated(book)
    }
  }

  private fun onBookStatusFailedDownload(
    bookStatus: BookStatus.FailedDownload,
    book: Book
  ) {
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.VISIBLE)
    this.setVisibilityIfNecessary(this.idle, View.GONE)
    this.setVisibilityIfNecessary(this.progress, View.GONE)

    this.errorDismiss.setOnClickListener {
      this.listener.dismissBorrowError(this.feedEntry as FeedEntryOPDS)
    }
    this.errorDetails.setOnClickListener {
      this.listener.showTaskError(book, bookStatus.result)
    }
    this.errorRetry.setOnClickListener {
      this.listener.borrowMaybeAuthenticated(book)
    }
  }

  private fun onBookStatusFailedLoan(
    bookStatus: BookStatus.FailedLoan,
    book: Book
  ) {
    //If fail is due to token expiration, treat specially
    if (bookStatus.message.contains("401") || bookStatus.message.contains("400")) {
      logger.debug("token refresh failed, trigger popup")
      if (!popUpShown) {
        onLogInNeeded(book)
      }
    }
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.VISIBLE)
    this.setVisibilityIfNecessary(this.idle, View.GONE)
    this.setVisibilityIfNecessary(this.progress, View.GONE)

    this.errorDismiss.setOnClickListener {
      this.listener.dismissBorrowError(this.feedEntry as FeedEntryOPDS)
    }
    this.errorDetails.setOnClickListener {
      this.listener.showTaskError(book, bookStatus.result)
    }
    this.errorRetry.setOnClickListener {
      this.listener.borrowMaybeAuthenticated(book)
    }
  }


  /**
   * Show a popup informing user of token expiration and open up the login page when dismissed.
   */
  private fun onLogInNeeded(book: Book) {
    logger.debug("Showing 'Please login' popup")
    //Ensure only one popup is shown at a time
    popUpShown = true
    val builder: AlertDialog.Builder = AlertDialog.Builder(this.context)
    builder
      .setMessage(R.string.bookSessionExpiredMessage)
      .setTitle(R.string.bookSessionExpiredTitle)
      .setPositiveButton(R.string.bookSessionExpiredButton) { dialog, which ->
        listener.openLoginDialog(book.account)
        popUpShown = false
        dialog.dismiss()
      }

    val dialog: AlertDialog = builder.create()
    dialog.show()

  }

  private fun onBookStatusLoanedNotDownloaded(
    bookStatus: BookStatus.Loaned.LoanedNotDownloaded,
    book: Book
  ) {
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.GONE)
    this.setVisibilityIfNecessary(this.idle, View.VISIBLE)
    this.setVisibilityIfNecessary(this.progress, View.GONE)
    this.setVisibilityIfNecessary(this.idleLoanTime, View.VISIBLE)

    this.idleButtons.removeAllViews()

    val loanDuration = getLoanDuration(book)

    this.idleLoanTime.text = context.getString(R.string.catalogLoanTime, loanDuration)

    this.idleButtons.addView(
      when {
        bookStatus.isOpenAccess -> {
          this.buttonCreator.createGetButton(
            onClick = {
              this.listener.borrowMaybeAuthenticated(book)
            }
          )
        }
        else -> {
          this.buttonCreator.createDownloadButton(
            onClick = {
              this.listener.borrowMaybeAuthenticated(book)
            }
          )
        }
      }
    )

    if (isBookReturnable(book)) {
      this.idleButtons.addView(this.buttonCreator.createButtonSpace())
      this.idleButtons.addView(
        this.buttonCreator.createRevokeLoanButton(
          onClick = {
            this.revokeLoanPopup(book)
          }
        )
      )
    } else if (isBookDeletable(book)) {
      this.idleButtons.addView(this.buttonCreator.createButtonSpace())
      this.idleButtons.addView(
        this.buttonCreator.createRevokeLoanButton(
          onClick = {
            this.listener.delete(this.feedEntry as FeedEntryOPDS)
          }
        )
      )
    } else {
      this.idleButtons.addView(this.buttonCreator.createButtonSpace())
    }
  }

  private fun onBookStatusLoanable(book: Book) {
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.GONE)
    this.setVisibilityIfNecessary(this.idle, View.VISIBLE)
    this.setVisibilityIfNecessary(this.progress, View.GONE)
    this.setVisibilityIfNecessary(this.idleLoanTime, View.GONE)

    this.idleButtons.removeAllViews()
    this.idleButtons.addView(
      this.buttonCreator.createGetButton(
        onClick = {
          this.listener.borrowMaybeAuthenticated(book)
        }
      )
    )
    this.idleButtons.addView(this.buttonCreator.createButtonSpace())
  }

  private fun onBookStatusReachedLoanLimit() {
    MaterialAlertDialogBuilder(context)
      .setTitle(R.string.bookReachedLoanLimitDialogTitle)
      .setMessage(R.string.bookReachedLoanLimitDialogMessage)
      .setPositiveButton(R.string.bookReachedLoanLimitDialogButton) { dialog, _ ->
        dialog.dismiss()
      }
      .create()
      .show()

    this.listener.resetInitialBookStatus(this.feedEntry as FeedEntryOPDS)
  }

  private fun onBookStatusHoldable(book: Book) {
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.GONE)
    this.setVisibilityIfNecessary(this.idle, View.VISIBLE)
    this.setVisibilityIfNecessary(this.progress, View.GONE)
    this.setVisibilityIfNecessary(this.idleLoanTime, View.GONE)

    this.idleButtons.removeAllViews()
    this.idleButtons.addView(
      this.buttonCreator.createReserveButton(
        onClick = {
          this.listener.reserveMaybeAuthenticated(book)
        }
      )
    )
    this.idleButtons.addView(this.buttonCreator.createButtonSpace())
  }

  private fun onBookStatusHeldReady(
    status: BookStatus.Held.HeldReady,
    book: Book
  ) {
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.GONE)
    this.setVisibilityIfNecessary(this.idle, View.VISIBLE)
    this.setVisibilityIfNecessary(this.progress, View.GONE)
    this.setVisibilityIfNecessary(this.idleLoanTime, View.INVISIBLE)


    this.idleButtons.removeAllViews()
    this.idleButtons.addView(
      this.buttonCreator.createGetButton(
        onClick = {
          this.listener.borrowMaybeAuthenticated(book)
        }
      )
    )

    if (status.isRevocable) {
      this.idleButtons.addView(
        this.buttonCreator.createButtonSpace()
      )
      this.idleButtons.addView(
        this.buttonCreator.createRevokeHoldButton(
          onClick = {
            this.revokeHoldPopup(book)
          }
        )
      )
    } else {
      this.idleButtons.addView(this.buttonCreator.createButtonSpace())
    }
  }

  private fun onBookStatusHeldInQueue(
    status: BookStatus.Held.HeldInQueue,
    book: Book
  ) {
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.GONE)
    this.setVisibilityIfNecessary(this.idle, View.VISIBLE)
    this.setVisibilityIfNecessary(this.progress, View.GONE)
    this.setVisibilityIfNecessary(this.idleLoanTime, View.VISIBLE)

    // Show a short version of the days remaining on hold in the loan time box
    this.idleLoanTime.text =
      CatalogBookAvailabilityStrings.onHeldShort(this.context.resources, status)

    this.idleButtons.removeAllViews()
    if (status.isRevocable) {
      this.idleButtons.addView(
        this.buttonCreator.createRevokeHoldButton(
          onClick = {
            this.revokeHoldPopup(book)
          }
        )
      )
      this.idleButtons.addView(this.buttonCreator.createButtonSpace())
    } else {
      this.idleButtons.addView(
        this.buttonCreator.createCenteredTextForButtons(R.string.catalogHoldCannotCancel)
      )
    }
  }

  private fun onBookStatusLoanedDownloaded(
    bookStatus: BookStatus.Loaned.LoanedDownloaded,
    book: Book
  ) {
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.GONE)
    this.setVisibilityIfNecessary(this.idle, View.VISIBLE)
    this.setVisibilityIfNecessary(this.progress, View.GONE)
    this.setVisibilityIfNecessary(this.idleLoanTime, View.VISIBLE)

    val loanDuration = getLoanDuration(book)

    //Show how long the book is on loan for
    this.idleLoanTime.text = context.getString(R.string.catalogLoanTime, loanDuration)

    this.idleButtons.removeAllViews()

    when (val format = book.findPreferredFormat()) {
      is BookFormat.BookFormatPDF,
      is BookFormat.BookFormatEPUB -> {
        this.idleButtons.addView(
          this.buttonCreator.createReadButton(
            onClick = {
              this.listener.openViewer(book, format)
            }
          )
        )
      }
      is BookFormat.BookFormatAudioBook -> {
        this.idleButtons.addView(
          this.buttonCreator.createListenButton(
            onClick = {
              this.listener.openViewer(book, format)
            }
          )
        )
      }
      null -> {
        this.idleButtons.addView(this.buttonCreator.createButtonSizedSpace())
      }
    }

    if (isBookReturnable(book)) {
      this.idleButtons.addView(this.buttonCreator.createButtonSpace())
      this.idleButtons.addView(
        this.buttonCreator.createRevokeLoanButton(
          onClick = {
            //Show popup asking to confirm revoking the book
            this.revokeLoanPopup(book)
          }
        )
      )
    } else if (isBookDeletable(book)) {
      this.idleButtons.addView(this.buttonCreator.createButtonSpace())
      this.idleButtons.addView(
        this.buttonCreator.createRevokeLoanButton(
          onClick = {
            this.listener.delete(this.feedEntry as FeedEntryOPDS)
          }
        )
      )
    } else {
      this.idleButtons.addView(this.buttonCreator.createButtonSpace())
    }
  }
  /**
  * Show user a popup requiring user to confirm a loan return
  */
  private fun revokeLoanPopup(book: Book) {
    //Mark that a popup is currently shown
    popUpShown = true
    logger.debug("Showing book return popup")
    val builder: AlertDialog.Builder = AlertDialog.Builder(this.context)
    builder
      .setTitle(context.getString(R.string.bookConfirmReturnTitle, book.entry.title))
      .setMessage(R.string.bookConfirmReturnMessage)
      .setPositiveButton(R.string.bookConfirmReturnConfirmButton) { dialog, which ->
        //Set the popup as closed
        //And start revoke
        this.listener.revokeMaybeAuthenticated(book)
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
    val builder: AlertDialog.Builder = AlertDialog.Builder(this.context)
    builder
      .setTitle(context.getString(R.string.bookConfirmRevokeTitle, book.entry.title))
      .setMessage(R.string.bookConfirmRevokeMessage)
      .setPositiveButton(R.string.bookConfirmRevokeConfirmButton) { dialog, which ->
        //Set the popup as closed
        //And start revoke
        this.listener.revokeMaybeAuthenticated(book)
        popUpShown = false
      }
      .setNeutralButton(R.string.bookConfirmReturnCancelButton) { dialog, which ->
        //Do nothing, don't revoke the book
        popUpShown = false
      }

    val dialog: AlertDialog = builder.create()
    dialog.show()
  }

  @Suppress("UNUSED_PARAMETER")
  private fun onBookStatusRevoked(book: BookWithStatus) {
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.GONE)
    this.setVisibilityIfNecessary(this.idle, View.VISIBLE)
    this.setVisibilityIfNecessary(this.progress, View.GONE)
    this.setVisibilityIfNecessary(this.idleLoanTime, View.GONE)

    this.idleButtons.removeAllViews()
  }

  private fun onBookStatusDownloading(
    book: BookWithStatus,
    status: BookStatus.Downloading
  ) {
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.GONE)
    this.setVisibilityIfNecessary(this.idle, View.GONE)
    this.setVisibilityIfNecessary(this.progress, View.VISIBLE)

    this.progressText.text = book.book.entry.title
    //Add an onClick listener to the book cell
    //that links to the book's detail view
    val onClick: (View) -> Unit = {
      logger.debug("Open book detail view")
      this.listener.openBookDetail(this.feedEntry as FeedEntryOPDS)
    }
    //Set the clickable area as the whole cell
    this.progress.setOnClickListener(onClick)


    //Check file size, and show popup if file is too big
    //Check is done only once for each book download

    //Did somehow skip every now and then the case of totalBytes == 0L
    //So the check is now done on one of the first packets
    if (status.currentTotalBytes!! < 10000L) {
      //Expected size of the book that is downloading
      val expectedSize = status.expectedTotalBytes
      //How much space is free on the device
      val freeSpace = getInternalMem()
      this.logger.debug("Assumed size of file: {}", formatSize(expectedSize))

      //Never should be null, but needs to be checked
      if (expectedSize != null) {
        //If size smaller than internal memory, it should technically fit to memory
        //If doesn't, user gets shown a popup and download is cancelled
        if (expectedSize < freeSpace) {
          logger.debug("Enough space for download")
          logger.debug("Expected size: {}", expectedSize)
          logger.debug(
            "Remaining space: {}",
            formatSize(freeSpace - expectedSize)
          )
        } else {
          logger.debug("Not enough space for download")
          logger.debug("Already a popup showing: {}", popUpShown)
          //We don't want to show multiple popups ontop of one another, so we
          //Show one if one is not already shown
          if (!popUpShown) {
            //Show the popup
            onFileTooBigToStore(freeSpace, expectedSize - freeSpace)
            //Cancel the download
            this.listener.cancelDownload(this.feedEntry as FeedEntryOPDS)
          }
        }
      }
    }


    val progressPercent = status.progressPercent?.toInt()
    if (progressPercent != null) {
      this.progressProgress.isIndeterminate = false
      this.progressProgress.progress = progressPercent
    } else {
      this.progressProgress.isIndeterminate = true
    }
  }

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
   * Change the bit presentation of a number to
   * a better understandable form.
   * Returns a string with the size suffix added.
   */
  private fun formatSize(number : Long?) : String {
    //Expected size in bits that gets changed to the kilobyte or megabyte presentations
    var expSize: Long = number?: 0L
    //Suffix, that is either KB or MB
    var suffix: String? = null
    //Divide with 1024 to ge the kilobyte presentation, set the suffix
    if (expSize >= 1024) {
      suffix = "KB"
      expSize /= 1024
      //If possible, divide again to get megabyte presentation, set the suffix
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
    //Return the size as string
    return expSizeString.toString()
  }

  //Boolean that is used to only show one popup at a time
  //Only true when there is a popup that is currently shown
  private var popUpShown = false

  /**
   * If there is no space for the book on the device, show a popup that informs the user about the
   * required space.
   */
  private fun onFileTooBigToStore(deviceSpace: Long, neededSpace : Long) {
    //Set the popup as shown
    popUpShown = true
    logger.debug("Showing size info")
    //Show a popup with the device space and needed space
    val builder: AlertDialog.Builder = AlertDialog.Builder(this.context)
    builder
      .setMessage(this.context.getString(
        R.string.bookNotEnoughSpaceMessage,
        formatSize(deviceSpace),
        formatSize(neededSpace)))
      .setTitle(R.string.bookNotEnoughSpaceTitle)
      .setPositiveButton(R.string.bookNotEnoughSpaceButton) { dialog, which ->
        //Set the popup as closed
        popUpShown = false
        dialog.dismiss()
      }

    val dialog: AlertDialog = builder.create()
    dialog.show()
  }
  private fun onBookStatusDownloadWaitingForExternalAuthentication(
    book: Book
  ) {
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.GONE)
    this.setVisibilityIfNecessary(this.idle, View.GONE)
    this.setVisibilityIfNecessary(this.progress, View.VISIBLE)

    this.progressText.text = book.entry.title
    this.progressProgress.isIndeterminate = true
  }

  private fun onBookStatusDownloadExternalAuthenticationInProgress(
    book: Book
  ) {
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.GONE)
    this.setVisibilityIfNecessary(this.idle, View.GONE)
    this.setVisibilityIfNecessary(this.progress, View.VISIBLE)

    this.progressText.text = book.entry.title
    this.progressProgress.isIndeterminate = true
  }

  /**
   * Show toast informing user that the book has been added to selected books
   * and reset bookStatus to what it was before selection
   */
  private fun onBookStatusSelected(book: BookWithStatus) {
    Toast.makeText(this.context, context.getString(R.string.catalogBookSelect, book.book.entry.title), Toast.LENGTH_SHORT).show()
    this.listener.resetPreviousBookStatus(book.book.id, book.status, true)
  }

  /**
   * Show toast informing user that the book has been removed from selected books
   * and reset bookStatus to what it was before
   */
  private fun onBookStatusUnselected(book: BookWithStatus) {
    Toast.makeText(this.context, context.getString(R.string.catalogBookUnselect, book.book.entry.title), Toast.LENGTH_SHORT).show()
    this.listener.resetPreviousBookStatus(book.book.id, book.status, false)
  }

  fun unbind() {
    val currentFeedEntry = this.feedEntry
    if (currentFeedEntry is FeedEntryOPDS) {
      this.listener.unregisterObserver(currentFeedEntry, this::onBookChanged)
    }

    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.GONE)
    this.setVisibilityIfNecessary(this.idle, View.GONE)
    this.setVisibilityIfNecessary(this.progress, View.GONE)
    this.setVisibilityIfNecessary(this.idleLoanTime, View.GONE)

    this.errorDetails.setOnClickListener(null)
    this.errorDismiss.setOnClickListener(null)
    this.errorRetry.setOnClickListener(null)
    this.idle.setOnClickListener(null)
    this.idleAuthor.text = null
    this.idleButtons.removeAllViews()
    this.idleCover.contentDescription = null
    this.idleCover.setImageDrawable(null)
    this.idleCover.setOnClickListener(null)
    this.idleTitle.setOnClickListener(null)
    this.idleTitle.text = null
    this.progress.setOnClickListener(null)
    this.progressText.setOnClickListener(null)
    this.idleLoanTime.text = null

    this.thumbnailLoading = this.thumbnailLoading?.let { loading ->
      loading.cancel(true)
      null
    }
  }

  private fun getLoanDuration(book: Book): String {
    val status = BookStatus.fromBook(book)
    return if (status is BookStatus.Loaned.LoanedDownloaded ||
      status is BookStatus.Loaned.LoanedNotDownloaded
    ) {
      val endDate = (status as? BookStatus.Loaned.LoanedDownloaded)?.loanExpiryDate
        ?: (status as? BookStatus.Loaned.LoanedNotDownloaded)?.loanExpiryDate

      if (
        endDate != null
      ) {
        CatalogBookAvailabilityStrings.intervalStringLoanDuration(
          this.context.resources,
          DateTime.now(),
          endDate
        )
      } else {
        ""
      }
    } else {
      ""
    }
  }

  private fun isBookReturnable(book: Book): Boolean {
    val profile = this.profilesController.profileCurrent()
    val account = profile.account(book.account)

    return try {
      if (account.bookDatabase.books().contains(book.id)) {
        when (val status = BookStatus.fromBook(book)) {
          is BookStatus.Loaned.LoanedDownloaded ->
            status.returnable
          is BookStatus.Loaned.LoanedNotDownloaded ->
            true
          else ->
            false
        }
      } else {
        false
      }
    } catch (e: Exception) {
      false
    }
  }

  private fun isBookDeletable(book: Book): Boolean {
    return try {
      val profile = this.profilesController.profileCurrent()
      val account = profile.account(book.account)
      return if (account.bookDatabase.books().contains(book.id)) {
        book.entry.availability.matchAvailability(
          object : OPDSAvailabilityMatcherType<Boolean, Exception> {
            override fun onHeldReady(availability: OPDSAvailabilityHeldReady): Boolean =
              false

            override fun onHeld(availability: OPDSAvailabilityHeld): Boolean =
              false

            override fun onHoldable(availability: OPDSAvailabilityHoldable): Boolean =
              false

            override fun onLoaned(availability: OPDSAvailabilityLoaned): Boolean =
              availability.revoke.isNone && book.isDownloaded

            override fun onLoanable(availability: OPDSAvailabilityLoanable): Boolean =
              true

            override fun onOpenAccess(availability: OPDSAvailabilityOpenAccess): Boolean =
              true

            override fun onRevoked(availability: OPDSAvailabilityRevoked): Boolean =
              false
          })
      } else {
        false
      }
    } catch (e: Exception) {
      false
    }
  }
}
