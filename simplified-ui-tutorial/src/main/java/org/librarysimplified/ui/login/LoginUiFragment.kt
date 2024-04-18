package org.librarysimplified.ui.login

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import org.librarysimplified.ui.tutorial.R
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.listeners.api.fragmentListeners
import org.slf4j.LoggerFactory

class LoginUiFragment : Fragment(R.layout.login_fragment) {
  private val logger = LoggerFactory.getLogger(LoginUiFragment::class.java)
  private val listener: FragmentListenerType<LoginEvent> by fragmentListeners()
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val loginSuomiFiButton = view.findViewById<Button>(R.id.ekirjasto_loginSuomiFi)
    val loginPasskeyButton = view.findViewById<Button>(R.id.ekirjasto_loginPasskey)
    val loginSkipButton = view.findViewById<Button>(R.id.ekirjasto_loginSkip)

    loginSuomiFiButton!!.setOnClickListener { loginSuomiFi() }
    loginPasskeyButton!!.setOnClickListener { loginPasskey() }
    loginSkipButton!!.setOnClickListener { skipLogin() }
  }
  private fun skipLogin() {
    this.listener.post(LoginEvent.SkipLogin)
  }

  private fun loginSuomiFi() {
    this.listener.post(LoginEvent.StartLoginSuomiFi)
  }

  private fun loginPasskey() {
    this.listener.post(LoginEvent.StartLoginPasskey)
  }
}
