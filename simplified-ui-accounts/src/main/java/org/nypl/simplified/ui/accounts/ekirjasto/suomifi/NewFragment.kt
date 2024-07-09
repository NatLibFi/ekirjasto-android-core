package org.nypl.simplified.ui.accounts.ekirjasto.suomifi

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import org.librarysimplified.ui.accounts.R

class NewFragment : Fragment(R.layout.new_view) {

  private val patron by lazy { arguments?.getString(PATRON_ID) }

  private lateinit var patronTextView: TextView

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    this.patronTextView = view.findViewById(R.id.newTextField)
  }

  override fun onStart() {
    super.onStart()

    this.patronTextView.text = patron
  }
  companion object {
    private const val PATRON_ID = "org.nypl.simplified.ui.accounts.ekirjasto.suomifi.patron"

    fun create(patron: String?) = NewFragment().apply {
      arguments = bundleOf(PATRON_ID to patron)
    }
  }
}
