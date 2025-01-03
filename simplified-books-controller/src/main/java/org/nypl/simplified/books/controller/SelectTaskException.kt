package org.nypl.simplified.books.controller

sealed class SelectTaskException : Exception() {
  class SelectTaskFailed : SelectTaskException()
  class UnselectTaskFailed : SelectTaskException()
  class SelectAccessTokenExpired : SelectTaskException()
}
