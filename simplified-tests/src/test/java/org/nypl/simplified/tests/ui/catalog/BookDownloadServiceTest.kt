package org.nypl.simplified.tests.ui.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.librarysimplified.ui.catalog.BookDownloadService
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.BookID
import org.robolectric.Robolectric
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.objenesis.ObjenesisStd

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BookDownloadServiceTest {

  @Test
  fun `finishing one of multiple downloads removes only that download and promotes the next one`() {
    val service = Robolectric.buildService(BookDownloadService::class.java).create().get()
    val firstBook = BookID.create("firstbook")
    val secondBook = BookID.create("secondbook")

    val downloads = service.privateField<MutableMap<BookID, Any>>("downloads")
    val firstDownload = download(firstBook, "First book", 1001)
    val secondDownload = download(secondBook, "Second book", 1002)
    downloads[firstBook] = firstDownload
    downloads[secondBook] = secondDownload
    service.setPrivateField("foregroundBookID", firstBook)

    service.privateMethod("finishDownload", BookID::class.java, Int::class.javaPrimitiveType!!)
      .invoke(service, firstBook, 1)

    assertFalse(downloads.containsKey(firstBook))
    assertTrue(downloads.containsKey(secondBook))
    assertEquals(secondBook, service.privateField<BookID?>("foregroundBookID"))
  }

  private fun download(
    bookID: BookID,
    title: String,
    notificationID: Int
  ): Any {
    val downloadClass = Class.forName(
      "org.librarysimplified.ui.catalog.BookDownloadService\$Download"
    )
    @Suppress("UNCHECKED_CAST")
    val download = ObjenesisStd().newInstance(downloadClass as Class<Any>)
    download.setPrivateField("accountID", AccountID.generate())
    download.setPrivateField("bookID", bookID)
    download.setPrivateField("title", title)
    download.setPrivateField("notificationID", notificationID)
    return download
  }

  private fun <T> Any.privateField(name: String): T {
    val field = javaClass.getDeclaredField(name)
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return field.get(this) as T
  }

  private fun Any.setPrivateField(name: String, value: Any?) {
    val field = javaClass.getDeclaredField(name)
    field.isAccessible = true
    field.set(this, value)
  }

  private fun Any.privateMethod(name: String, vararg parameterTypes: Class<*>): java.lang.reflect.Method {
    val method = javaClass.getDeclaredMethod(name, *parameterTypes)
    method.isAccessible = true
    return method
  }
}
