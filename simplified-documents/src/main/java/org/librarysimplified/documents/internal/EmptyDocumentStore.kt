package org.librarysimplified.documents.internal

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import org.librarysimplified.documents.DocumentStoreType
import org.librarysimplified.documents.DocumentType
import org.librarysimplified.documents.EULAType

internal object EmptyDocumentStore : DocumentStoreType {
  override val privacyPolicy: DocumentType? =
    null
  override val feedback: DocumentType? =
    null
  override val accessibilityStatement: DocumentType? =
    null
  override val about: DocumentType? =
    null
  override val acknowledgements: DocumentType? =
    null
  override val eula: EULAType? =
    null
  override val licenses: DocumentType? =
    null
  override val instructionsFI: DocumentType? =
    null
  override val instructionsSV: DocumentType? =
    null
  override val instructionsEN: DocumentType? =
    null
  override val faq: DocumentType? =
    null

  override fun update(executor: ListeningExecutorService): ListenableFuture<*> {
    return Futures.immediateFuture(Unit)
  }
}
