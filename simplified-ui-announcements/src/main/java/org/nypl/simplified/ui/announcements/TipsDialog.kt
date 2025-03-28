package org.nypl.simplified.ui.announcements

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import org.librarysimplified.ui.announcements.R
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.listeners.api.fragmentListeners

class TipsDialog : DialogFragment(R.layout.tips_dialog) {

  private val listener: FragmentListenerType<TipsEvent> by fragmentListeners()

  private lateinit var title: TextView
  private lateinit var content: TextView
  private lateinit var okButton: Button
  private lateinit var dismissButton: Button

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    this.title = view.findViewById(R.id.tips_title)
    this.okButton = view.findViewById(R.id.tips_ok)
    this.dismissButton = view.findViewById(R.id.tips_dismiss)
    this.content = view.findViewById(R.id.tips_content)
  }

  override fun onStart() {
    super.onStart()
    dialog?.window?.setLayout(
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.WRAP_CONTENT
    )
    this.okButton.setOnClickListener {
      //Close dialog
      this.dismiss()
    }

    this.dismissButton.setOnClickListener {
      //Close dialog and mark tips to not be shown again
      this.dismiss()
      listener.post(TipsEvent.DismissTips)
    }
  }
}
