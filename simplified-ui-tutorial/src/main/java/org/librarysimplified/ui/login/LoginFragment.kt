package org.nypl.simplified.ui.login

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import org.librarysimplified.ui.tutorial.R
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.listeners.api.fragmentListeners


class LoginFragment : Fragment(R.layout.login_fragment) {
  private val listener: FragmentListenerType<LoginEvent> by fragmentListeners()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)


    val loginButton = view.findViewById<Button>(R.id.ekirjasto_loginButton)
    val regButton = view.findViewById<Button>(R.id.ekirjasto_regButton)

    loginButton!!.setOnClickListener { ekirjastoLogin() }
    regButton!!.setOnClickListener { ekirjastoReg() }
  }

  private fun ekirjastoLogin() {
    this.listener.post(LoginEvent.StartLogin)
  }

  private fun ekirjastoReg() {
    this.listener.post(LoginEvent.StartLogin)
  }
}
