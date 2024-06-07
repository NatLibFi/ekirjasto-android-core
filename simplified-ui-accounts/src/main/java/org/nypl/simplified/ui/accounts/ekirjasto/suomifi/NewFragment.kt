package org.nypl.simplified.ui.accounts.ekirjasto.suomifi

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import org.librarysimplified.ui.accounts.R

class NewFragment : Fragment(R.layout.new_view) {
 fun create() : NewFragment {
   val fragment = NewFragment()
   return fragment
 }
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
  }
}
