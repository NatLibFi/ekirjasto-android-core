package org.nypl.simplified.ui.accounts.ekirjastosuomifi

import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import io.reactivex.disposables.CompositeDisposable
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.listeners.api.fragmentListeners
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskStep
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.nypl.simplified.webview.WebViewUtilities
import org.librarysimplified.ui.accounts.R

/**
 * A fragment that performs the E-kirjasto login workflow.
 */

class AccountEkirjastoSuomiFiFragment : Fragment(R.layout.account_ekirjastosuomifi) {

  companion object {

    private const val PARAMETERS_ID =
      "org.nypl.simplified.ui.accounts.ekirjastosuomifi.AccountEkirjastoSuomiFiFragment"

    /**
     * Create a new account fragment for the given parameters.
     */

    fun create(parameters: AccountEkirjastoSuomiFiFragmentParameters): AccountEkirjastoSuomiFiFragment {
      val fragment = AccountEkirjastoSuomiFiFragment()
      fragment.arguments = bundleOf(this.PARAMETERS_ID to parameters)
      return fragment
    }
  }

  private val eventSubscriptions: CompositeDisposable =
    CompositeDisposable()

  private val listener: FragmentListenerType<AccountEkirjastoSuomiFiEvent> by fragmentListeners()

  private val parameters: AccountEkirjastoSuomiFiFragmentParameters by lazy {
    this.requireArguments()[PARAMETERS_ID] as AccountEkirjastoSuomiFiFragmentParameters
  }

  private val viewModel: AccountEkirjastoSuomiFiViewModel by viewModels(
    factoryProducer = {
      AccountEkirjastoSuomiFiViewModelFactory(
        application = this.requireActivity().application,
        account = this.parameters.accountID,
        description = this.parameters.authenticationDescription
      )
    }
  )

  private lateinit var progress: ProgressBar
  private lateinit var webView: WebView

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    this.progress = view.findViewById(R.id.suomifiprogressBar)
    this.webView = view.findViewById(R.id.suomifiWebView)
    WebViewUtilities.setForcedDark(this.webView.settings, resources.configuration)
  }

  override fun onStart() {
    super.onStart()

    this.eventSubscriptions.add(
      this.viewModel.events.subscribe(
        this::onSuomiFiEvent,
        this::onSuomiFiEventException,
        this::onSuomiFiEventFinished
      )
    )

    this.webView.webChromeClient = AccountEkirjastoSuomiFiChromeClient(this.progress)
    this.webView.webViewClient = this.viewModel.webViewClient
    this.webView.settings.javaScriptEnabled = true

    if (this.viewModel.isWebViewClientReady) {
      this.loadLoginPage()
    }
  }

  private fun loadLoginPage() {
    val urlSuffix = "&state=app"
    this.webView.loadUrl("${this.parameters.authenticationDescription.tunnistus_start}${urlSuffix}")
  }

  private fun onSuomiFiEvent(event: AccountEkirjastoSuomiFiInternalEvent) {
    return when (event) {
      is AccountEkirjastoSuomiFiInternalEvent.WebViewClientReady ->
        this.onWebViewClientReady()
      is AccountEkirjastoSuomiFiInternalEvent.Failed ->
        this.onSuomiFiEventFailed(event)
      is AccountEkirjastoSuomiFiInternalEvent.AccessTokenStartReceive ->
        this.onSuomiFiEventAccessTokenStartReceive()
      is AccountEkirjastoSuomiFiInternalEvent.AccessTokenObtained ->
        this.onSuomiFiEventAccessTokenObtained()
    }
  }

  private fun onSuomiFiEventAccessTokenStartReceive() {
    this.webView.visibility = View.GONE;
  }

  private fun onWebViewClientReady() {
    this.loadLoginPage()
  }

  private fun onSuomiFiEventAccessTokenObtained() {
    this.listener.post(AccountEkirjastoSuomiFiEvent.AccessTokenObtained)
  }

  private fun onSuomiFiEventFailed(event: AccountEkirjastoSuomiFiInternalEvent.Failed) {
    val newDialog =
      AlertDialog.Builder(this.requireActivity())
        .setTitle(R.string.accountCreationFailed)
        .setMessage(R.string.accountCreationFailedMessage)
        .setPositiveButton(R.string.accountsDetails) { dialog, _ ->
          this.showErrorPage(this.makeLoginTaskSteps(event.message))
          dialog.dismiss()
        }.create()
    newDialog.show()
  }

  private fun makeLoginTaskSteps(
    message: String
  ): List<TaskStep> {
    val taskRecorder = TaskRecorder.create()
    taskRecorder.beginNewStep("Started E-kirjasto login...")
    taskRecorder.currentStepFailed(message, "suomifiAccountCreationFailed")
    return taskRecorder.finishFailure<AccountType>().steps
  }

  private fun showErrorPage(taskSteps: List<TaskStep>) {
    val parameters =
      ErrorPageParameters(
        emailAddress = this.viewModel.supportEmailAddress,
        body = "",
        subject = "[simplye-error-report]",
        attributes = sortedMapOf(),
        taskSteps = taskSteps
      )

    this.listener.post(AccountEkirjastoSuomiFiEvent.OpenErrorPage(parameters))
  }

  private fun onSuomiFiEventException(exception: Throwable) {
    this.showErrorPage(this.makeLoginTaskSteps(exception.message ?: exception.javaClass.name))
  }

  private fun onSuomiFiEventFinished() {
    // Don't care
  }

  override fun onStop() {
    super.onStop()
    this.eventSubscriptions.clear()
  }
}