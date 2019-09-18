package org.nypl.simplified.tests.local.books.reader.bookmarks

import org.nypl.simplified.books.reader.bookmarks.ReaderBookmarkService
import org.nypl.simplified.observable.ObservableType
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkEvent
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkHTTPCallsType
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkServiceProviderType
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkServiceType
import org.nypl.simplified.tests.books.reader.bookmarks.ReaderBookmarkServiceContract
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ReaderBookmarkServiceTest : ReaderBookmarkServiceContract() {

  override val logger: Logger = LoggerFactory.getLogger(ReaderBookmarkServiceTest::class.java)

  override fun bookmarkService(
    threads: (Runnable) -> Thread,
    events: ObservableType<ReaderBookmarkEvent>,
    httpCalls: ReaderBookmarkHTTPCallsType,
    profilesController: ProfilesControllerType): ReaderBookmarkServiceType {

    return ReaderBookmarkService.createService(
      ReaderBookmarkServiceProviderType.Requirements(
        threads = threads,
        events = events,
        httpCalls = httpCalls,
        profilesController = profilesController))
  }
}