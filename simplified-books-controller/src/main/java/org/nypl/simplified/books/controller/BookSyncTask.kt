package org.nypl.simplified.books.controller

import com.io7m.jfunctional.None
import com.io7m.jfunctional.Some
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.librarysimplified.mdc.MDCKeys
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.addCredentialsToProperties
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.getAccessToken
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountLoginState
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.BookIDs
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryType
import org.nypl.simplified.books.book_database.api.BookDatabaseException
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookWithStatus
import org.nypl.simplified.books.controller.api.BooksControllerType
import org.nypl.simplified.feeds.api.FeedLoaderType
import org.nypl.simplified.feeds.api.FeedLoading
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.opds.core.OPDSAvailabilityRevoked
import org.nypl.simplified.opds.core.OPDSFeedParserType
import org.nypl.simplified.opds.core.OPDSParseException
import org.nypl.simplified.patron.api.PatronUserProfile
import org.nypl.simplified.patron.api.PatronUserProfileParsersType
import org.nypl.simplified.profiles.api.ProfileID
import org.nypl.simplified.profiles.api.ProfilesDatabaseType
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.HashSet
import java.util.concurrent.TimeUnit

class BookSyncTask(
  private val accountID: AccountID,
  profileID: ProfileID,
  profiles: ProfilesDatabaseType,
  private val booksController: Controller,
  private val accountRegistry: AccountProviderRegistryType,
  private val bookRegistry: BookRegistryType,
  private val feedLoader: FeedLoaderType,
  private val patronParsers: PatronUserProfileParsersType,
  private val http: LSHTTPClientType,
  private val feedParser: OPDSFeedParserType
) : AbstractBookTask(accountID, profileID, profiles) {

  override val logger =
    LoggerFactory.getLogger(BookSyncTask::class.java)

  override val taskRecorder =
    TaskRecorder.create()

  override fun execute(account: AccountType): TaskResult.Success<Unit> {
    this.logger.debug("syncing account {}", account.id)
    this.taskRecorder.beginNewStep("Syncing...")

    //Handle keys
    MDC.put(MDCKeys.ACCOUNT_INTERNAL_ID, account.id.uuid.toString())
    MDC.put(MDCKeys.ACCOUNT_PROVIDER_NAME, account.provider.displayName)
    MDC.put(MDCKeys.ACCOUNT_PROVIDER_ID, account.provider.id.toString())

    //Update provider
    val provider = this.updateAccountProvider(account)
    val providerAuth = provider.authentication
    if (providerAuth == AccountProviderAuthenticationDescription.Anonymous) {
      this.logger.debug("account does not support syncing")
      return this.taskRecorder.finishSuccess(Unit)
    }

    //Get credentials to use for request authentication
    val credentials = account.loginState.credentials
    if (credentials == null) {
      this.logger.debug("no credentials, aborting!")
      return this.taskRecorder.finishSuccess(Unit)
    }

    this.fetchPatronUserProfile(
      account = account,
      credentials = credentials
    )

    //Get the loans stream
    //Continue execution only if successful
    //Otherwise it's useless and can trigger multiple refresh
    //requests in row, which makes the logout happen unnecessarily
    val loansStream: InputStream = fetchFeed(
      provider.loansURI,
      credentials,
      account
    ) ?: return this.taskRecorder.finishSuccess(Unit)

    //Get the selected stream
    val selectedStream: InputStream? = fetchFeed(
      provider.selectedURI,
      credentials,
      account
    )
    //If both fetches went fine, we combine the streams
    //And update database and registry
    if (selectedStream != null) {
      this.onHTTPOKMultipleFeeds(
        loansStream = loansStream,
        selectedStream = selectedStream,
        provider = provider,
        account = account
      )
    }
    return this.taskRecorder.finishSuccess(Unit)
  }

  /**
   * Fetch a feed from the URI provided.
   */
  private fun fetchFeed(
    uri: URI?,
    credentials: AccountAuthenticationCredentials,
    account: AccountType
  ) : InputStream? {
    if (uri == null) {
      this.logger.debug("no fetch URI, aborting!")
      this.taskRecorder.finishSuccess(Unit)
      return null
    }
    //Create the request
    val feedRequest =
      this.http.newRequest(uri)
        .setAuthorization(AccountAuthenticatedHTTP.createAuthorization(credentials))
        .addCredentialsToProperties(credentials)
        .build()

    //Execute the fetch
    val feedResponse = feedRequest.execute()
    return when (val status = feedResponse.status) {
      is LSHTTPResponseStatus.Responded.OK -> {
        //If answer is okay
        //Return the response stream
        status.bodyStream ?: ByteArrayInputStream(ByteArray(0))
      }
      is LSHTTPResponseStatus.Responded.Error -> {
        val recovered = this.onHTTPError(status, account)

        if (recovered) {
          this.taskRecorder.finishSuccess(Unit)
        } else {
          val message = String.format("%s: %d: %s", uri, status.properties.status, status.properties.message)
          val exception = IOException(message)
          this.taskRecorder.currentStepFailed(
            message = message,
            errorCode = "syncFailed",
            exception = exception
          )
          throw TaskFailedHandled(exception)
        }
        null
      }
      is LSHTTPResponseStatus.Failed ->
        throw IOException(status.exception)
    }
  }

  private fun fetchPatronUserProfile(
    account: AccountType,
    credentials: AccountAuthenticationCredentials
  ) {
    try {
      val profile =
        PatronUserProfiles.runPatronProfileRequest(
          taskRecorder = this.taskRecorder,
          patronParsers = this.patronParsers,
          credentials = credentials,
          http = this.http,
          account = account
        )

      account.updateCredentialsIfAvailable {
        this.withNewAnnotationsURI(it, profile)
      }
    } catch (e: Exception) {
      this.logger.error("patron user profile: ", e)
    }
  }

  private fun withNewAnnotationsURI(
    currentCredentials: AccountAuthenticationCredentials,
    profile: PatronUserProfile
  ): AccountAuthenticationCredentials {
    return when (currentCredentials) {
      is AccountAuthenticationCredentials.Basic ->
        currentCredentials.copy(annotationsURI = profile.annotationsURI)
      is AccountAuthenticationCredentials.BasicToken ->
        currentCredentials.copy(annotationsURI = profile.annotationsURI)
      is AccountAuthenticationCredentials.OAuthWithIntermediary ->
        currentCredentials.copy(annotationsURI = profile.annotationsURI)
      is AccountAuthenticationCredentials.SAML2_0 ->
        currentCredentials.copy(annotationsURI = profile.annotationsURI)
      is AccountAuthenticationCredentials.Ekirjasto ->
        currentCredentials.copy(annotationsURI = profile.annotationsURI)
    }
  }

  override fun onFailure(result: TaskResult.Failure<Unit>) {
    // Nothing to do
  }

  private fun updateAccountProvider(account: AccountType): AccountProviderType {
    this.logger.debug("resolving the existing account provider")

    val oldProvider = account.provider
    var newDescription =
      this.accountRegistry.findAccountProviderDescription(oldProvider.id)
    if (newDescription == null) {
      this.logger.debug("could not find account description for {} in registry", oldProvider.id)
      newDescription = oldProvider.toDescription()
    } else {
      this.logger.debug("found account description for {} in registry", oldProvider.id)
    }

    val newProviderResult =
      this.accountRegistry.resolve(
        { accountProvider, message ->
          this.logger.debug("[{}]: {}", accountProvider, message)
        },
        newDescription
      )

    return when (newProviderResult) {
      is TaskResult.Success -> {
        this.logger.debug("successfully resolved the account provider")
        account.setAccountProvider(newProviderResult.result)
        newProviderResult.result
      }
      is TaskResult.Failure -> {
        this.logger.error("failed to resolve account provider: ", newProviderResult.exception)
        oldProvider
      }
    }
  }

  /**
   * On successful HTTP request, update basicToken credentials
   * and parse the given feed, saving the books into the book registry.
   */
  @Throws(IOException::class)
  private fun onHTTPOK(
    stream: InputStream,
    provider: AccountProviderType,
    account: AccountType,
    accessToken: String?
  ) {
    account.updateBasicTokenCredentials(accessToken)
    stream.use { ok ->
      this.parseFeed(ok, provider, account)
    }
  }

  @Throws(OPDSParseException::class)
  private fun parseFeed(
    stream: InputStream,
    provider: AccountProviderType,
    account: AccountType
  ) {
    val feed = this.feedParser.parse(provider.loansURI, stream)

    /*
     * Obtain the set of books that are on disk already. If any
     * of these books are not in the received feed, then they have
     * expired and should be deleted.
     */

    val bookDatabase = account.bookDatabase
    val existing = bookDatabase.books()

    /*
     * Handle each book in the received feed.
     */

    val received = HashSet<BookID>(64)
    val entries = feed.feedEntries
    for (opdsEntry in entries) {
      val bookId = BookIDs.newFromOPDSEntry(opdsEntry)
      received.add(bookId)
      this.logger.debug("[{}] updating", bookId.brief())

      try {
        val databaseEntry = bookDatabase.createOrUpdate(bookId, opdsEntry)
        val book = databaseEntry.book
        this.bookRegistry.update(BookWithStatus(book, BookStatus.fromBook(book)))
      } catch (e: BookDatabaseException) {
        this.logger.error("[{}] unable to update database entry: ", bookId.brief(), e)
      }
    }

    /*
     * Now delete/revoke any book that previously existed, but is not in the
     * received set.
     */

    val revoking = HashSet<BookID>(existing.size)
    for (existingId in existing) {
      try {
        this.logger.debug("[{}] checking for deletion", existingId.brief())

        if (!received.contains(existingId)) {
          val dbEntry = bookDatabase.entry(existingId)
          val a = dbEntry.book.entry.availability
          if (a is OPDSAvailabilityRevoked) {
            revoking.add(existingId)
          } else {
            this.logger.debug("[{}] deleting", existingId.brief())
            this.updateRegistryForBook(account, dbEntry)
            dbEntry.delete()
          }
        } else {
          this.logger.debug("[{}] keeping", existingId.brief())
        }
      } catch (x: Throwable) {
        this.logger.error("[{}]: unable to delete entry: ", existingId.value(), x)
      }
    }

    /*
     * Finish the revocation of any books that need it.
     */

    for (revoke_id in revoking) {
      this.logger.debug("[{}] revoking", revoke_id.brief())
      this.booksController.bookRevoke(account.id, revoke_id)
    }
  }

  /**
   * Handle successful HTTP with multiple feeds
   */
  @Throws(IOException::class)
  private fun onHTTPOKMultipleFeeds(
    loansStream: InputStream,
    selectedStream: InputStream,
    provider: AccountProviderType,
    account: AccountType
  ) {
    //Parse multiple streams into one
    loansStream.use { loans ->
      this.parseSelectedAndLoansFeeds(loans, selectedStream, provider, account)
    }
  }

  /**
   * Parse together two feeds and add the books into the database. Removes from the
   * database entries that are not available in either of the feeds.
   */
  @Throws(OPDSParseException::class)
  private fun parseSelectedAndLoansFeeds(
    loansStream: InputStream,
    selectedStream: InputStream,
    provider: AccountProviderType,
    account: AccountType
  ) {
    //Parse loans
    val loansFeed = this.feedParser.parse(provider.selectedURI, loansStream)

    //Parse selected
    val selectedFeed = this.feedParser.parse(provider.selectedURI, selectedStream)

    //Combine the feed entries from both feeds into one list, create new IDs for them
    val loansMap = loansFeed.feedEntries.associateBy { BookIDs.newFromOPDSEntry(it) }
    val selectedMap = selectedFeed.feedEntries.associateBy { BookIDs.newFromOPDSEntry(it) }

    //Get all non-duplicate IDs
    val allBookIDs = (loansMap.keys + selectedMap.keys)

    //Initiate the list for the combined feed entries
    val combinedFeedEntries = mutableListOf<OPDSAcquisitionFeedEntry>()

    //Map values based on their id
    allBookIDs.map { id ->
      //Get the entries for id for both feeds
      val loansEntry = loansMap[id]
      val selectedEntry = selectedMap[id]

      //If there is a loans entry, use it as a base
      if (loansEntry != null) {
        //If there is a selected entry, we want to copy its selected value to the loans entry
        if (selectedEntry != null) {
          //Create new entry based on the loans entry and replace the values you want to replace
          //Which currently only selected info
          val newEntry = OPDSAcquisitionFeedEntry.newBuilderFrom(loansEntry)
            .setSelectedOption(selectedEntry.selected)
            .build()
          //Add the new entry
          combinedFeedEntries.add(newEntry)
        } else {
          // If book is not selected, we can just add the loans entry
          combinedFeedEntries.add(loansEntry)
        }
      } else {
        //If there is no loans entry, we can just add the selected entry
        combinedFeedEntries.add(selectedEntry!!)
      }
    }

    /*
     * Obtain the set of books that are on disk already. If any
     * of these books are not selected and not loaned, then they have
     * expired and/or are unselected and should be deleted.
     */

    val bookDatabase = account.bookDatabase
    val existing = bookDatabase.books()

    /*
     * Handle each book in the combined feed by checking if matching book is in database
     */
    val receivedBooks = HashSet<BookID>(64)
    for (opdsEntry in combinedFeedEntries) {
      // Create new id for the entry (will match the ID of the book in the registry, if any)
      val bookId = BookIDs.newFromOPDSEntry(opdsEntry)
      // Add to the received loans
      receivedBooks.add(bookId)

      this.logger.debug("[{}] updating", bookId.brief())

      // Try to add the book to the database
      // Update old entries or add new ones
      try {
        //Create a new database entry, or update old one
        val databaseEntry = bookDatabase.createOrUpdate(bookId, opdsEntry)
        //get the book we just added and refresh the registry
        val book = databaseEntry.book
        //Update the book state in the registry, create the status based on the book entry
        this.bookRegistry.update(BookWithStatus(book, BookStatus.fromBook(book)))
      } catch (e: BookDatabaseException) {
        this.logger.error("[{}] unable to update database entry: ", bookId.brief(), e)
      }
    }

    /*
     * Now delete/revoke any book that previously existed, but is not in the
     * received set of IDs we formed previously.
     */

    // Initiate the list into which we collect the book's IDs that need to be revoked
    //Not just deleted
    val revoking = HashSet<BookID>(existing.size)
    //Go through all id:s in database
    for (existingId in existing) {
      try {
        this.logger.debug("[{}] checking for deletion", existingId.brief())

        //If book not in loans or selected, handle it accordingly
        if (!allBookIDs.contains(existingId)) {
          val dbEntry = bookDatabase.entry(existingId)
          val a = dbEntry.book.entry.availability
          val b = dbEntry.book.entry.selected
          // If the book availability is revoked, it should be revoked
          if (a is OPDSAvailabilityRevoked) {
            revoking.add(existingId)
          } else {
            //Otherwise just deleting will do
            this.logger.debug("[{}] deleting", existingId.brief())
            //Load the single entry and add the "neutral" version to the book
            this.updateRegistryForBook(account, dbEntry)
            //Delete entry from database
            dbEntry.delete()
          }
        } else {
          this.logger.debug("[{}] keeping", existingId.brief())
        }
      } catch (x: Throwable) {
        this.logger.error("[{}]: unable to delete entry: ", existingId.value(), x)
      }
    }

    /*
     * Finish the revocation of any books that need it.
     */

    for (revoke_id in revoking) {
      this.logger.debug("[{}] revoking", revoke_id.brief())
      this.booksController.bookRevoke(account.id, revoke_id)
    }
  }

  private fun updateRegistryForBook(
    account: AccountType,
    dbEntry: BookDatabaseEntryType
  ) {
    this.logger.debug("attempting to fetch book permalink to update registry")
    val alternateOpt = dbEntry.book.entry.alternate
    return if (alternateOpt is Some<URI>) {
      val alternate = alternateOpt.get()
      val entry =
        FeedLoading.loadSingleEntryFeed(
          feedLoader = this.feedLoader,
          taskRecorder = this.taskRecorder,
          accountID = this.accountID,
          uri = alternate,
          timeout = Pair(30L, TimeUnit.SECONDS),
          method = "GET"
        )

      /*
       * Write a new book database entry based on the server state, and pretend that all the
       * books have been deleted. The code will delete the entire database entry after this
       * method returns anyway, so this code ensures that something sensible goes into the
       * book registry.
       */

      dbEntry.writeOPDSEntry(entry.feedEntry)
      val newBook = dbEntry.book.copy(formats = emptyList())
      val status = BookStatus.fromBook(newBook)

      this.logger.debug("book's new state is {}", status)
      this.bookRegistry.update(BookWithStatus(newBook, status))
    } else {
      throw IOException("No alternate link is available")
    }
  }

  /**
   * Returns whether we recovered from the error.
   */

  private fun onHTTPError(
    result: LSHTTPResponseStatus.Responded.Error,
    account: AccountType
  ): Boolean {
    when(account.loginState.credentials) {
      is AccountAuthenticationCredentials.Ekirjasto -> {
        //If the answer is 401, refresh needs to be triggered
        if (result.properties.status == 401) {
          //Create an exception that is handled in AbstractBookTask and forwarded to Controller,
          //From where the BookSyncTask was called
          val message = String.format("bookSync failed, bad credentials")
          val exception = IOException(message)
          //Fail the current step
          this.taskRecorder.currentStepFailed(
            message = message,
            errorCode = "accessTokenExpired",
            exception = exception
          )
          this.logger.debug("refresh credentials due to 401 server response")
          //Failure is checked and handled in Controller, where the tokenRefresh is triggered
          //Don't set as logged out, as can possibly be logged in with tokenRefresh
          throw TaskFailedHandled(exception)
        }
      }
      else -> {
        if (result.properties.status == 401) {
          this.logger.debug("removing credentials due to 401 server response")
          account.setLoginState(AccountLoginState.AccountNotLoggedIn)
          return true
        }
      }
    }

    return false
  }
}
