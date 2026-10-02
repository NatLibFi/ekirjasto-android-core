package org.librarysimplified.ui.catalog

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.reactivex.disposables.Disposable
import org.librarysimplified.services.api.Services
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.controller.api.BooksControllerType
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import java.util.concurrent.Executor

class BookDownloadService : Service() {
  private var statusSubscription: Disposable? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val accountID = intent?.serializableExtra<AccountID>(EXTRA_ACCOUNT_ID)
    val bookID = intent?.serializableExtra<BookID>(EXTRA_BOOK_ID)
    val entry = intent?.serializableExtra<OPDSAcquisitionFeedEntry>(EXTRA_ENTRY)
    if (accountID == null || bookID == null) {
      stopSelf(startId)
      return START_NOT_STICKY
    }

    val services = Services.serviceDirectory()
    val controller = services.requireService(BooksControllerType::class.java)
    if (intent.action == ACTION_CANCEL) {
      controller.bookCancelDownload(accountID, bookID)
      stopSelf(startId)
      return START_NOT_STICKY
    }
    if (entry == null) {
      stopSelf(startId)
      return START_NOT_STICKY
    }

    startForeground(NOTIFICATION_ID, createNotification(entry.title, accountID, bookID))
    statusSubscription?.dispose()
    statusSubscription = services.requireService(BookRegistryType::class.java).bookEvents()
      .filter { it.bookId == bookID }
      .subscribe { event ->
        val status = event.statusNow
        if (status is BookStatus.Downloading) {
          updateNotification(entry.title, accountID, bookID, status)
        }
      }
    controller
      .bookBorrow(accountID, bookID, entry)
      .addListener({ stopSelf(startId) }, Executor { it.run() })
    return START_NOT_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onDestroy() {
    statusSubscription?.dispose()
    statusSubscription = null
    super.onDestroy()
  }

  private fun createNotification(title: String, accountID: AccountID, bookID: BookID): Notification {
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL_ID, getString(R.string.catalogDownloading), NotificationManager.IMPORTANCE_LOW)
    )
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentTitle(getString(R.string.catalogDownloading))
      .setContentText(title)
      .setProgress(0, 0, true)
      .setOngoing(true)
      .addAction(cancelAction(accountID, bookID))
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .build()
  }

  private fun updateNotification(
    title: String,
    accountID: AccountID,
    bookID: BookID,
    status: BookStatus.Downloading
  ) {
    val progress = status.progressPercent?.toInt()
    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentTitle(getString(R.string.catalogDownloading))
      .setContentText(title)
      .setOngoing(true)
      .addAction(cancelAction(accountID, bookID))
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .apply {
        if (progress == null) setProgress(0, 0, true)
        else setProgress(100, progress.coerceIn(0, 100), false)
      }
      .build()
    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
  }

  private fun cancelAction(accountID: AccountID, bookID: BookID): NotificationCompat.Action {
    val intent = Intent(this, BookDownloadService::class.java).apply {
      action = ACTION_CANCEL
      putExtra(EXTRA_ACCOUNT_ID, accountID)
      putExtra(EXTRA_BOOK_ID, bookID)
    }
    val pendingIntent = PendingIntent.getService(
      this,
      bookID.hashCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    return NotificationCompat.Action(
      0,
      getString(R.string.catalogAccessibilityBookDownloadCancel),
      pendingIntent
    )
  }

  private inline fun <reified T : java.io.Serializable> Intent.serializableExtra(key: String): T? {
    return if (android.os.Build.VERSION.SDK_INT >= 33) {
      getSerializableExtra(key, T::class.java)
    } else {
      @Suppress("DEPRECATION")
      getSerializableExtra(key) as? T
    }
  }

  companion object {
    private const val CHANNEL_ID = "book_downloads"
    private const val NOTIFICATION_ID = 1001
    private const val ACTION_CANCEL = "book_download.cancel"
    const val EXTRA_ACCOUNT_ID = "book_download.account_id"
    const val EXTRA_BOOK_ID = "book_download.book_id"
    const val EXTRA_ENTRY = "book_download.entry"

    fun start(context: Context, accountID: AccountID, bookID: BookID, entry: OPDSAcquisitionFeedEntry) {
      ContextCompat.startForegroundService(context, Intent(context, BookDownloadService::class.java).apply {
        putExtra(EXTRA_ACCOUNT_ID, accountID)
        putExtra(EXTRA_BOOK_ID, bookID)
        putExtra(EXTRA_ENTRY, entry)
      })
    }
  }
}
