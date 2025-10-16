package org.librarysimplified.ui.catalog

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class CatalogRefreshViewModel: ViewModel() {
  private val _refreshMessage = MutableLiveData<String>()
  val refreshMessage: LiveData<String> get() = _refreshMessage

  fun setRefreshMessage(message: String) {
    _refreshMessage.value = message
  }
}

