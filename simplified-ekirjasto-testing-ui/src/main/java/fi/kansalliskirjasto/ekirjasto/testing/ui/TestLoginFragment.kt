package fi.kansalliskirjasto.ekirjasto.testing.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import fi.ekirjasto.testing.ui.BuildConfig
import fi.ekirjasto.testing.ui.R
import fi.kansalliskirjasto.ekirjasto.util.DataUtil
import fi.kansalliskirjasto.ekirjasto.testing.TestingOverrides
import org.nypl.simplified.android.ktx.supportActionBar
import org.slf4j.LoggerFactory


/**
 * A fragment for logging into the app as a test user.
 */
class TestLoginFragment(
  private val prefilledUsername: String = ""
) : Fragment(R.layout.testing) {
  private val logger = LoggerFactory.getLogger(this.javaClass)

  private val testLoginEnabled = BuildConfig.TEST_LOGIN_ENABLED

  private lateinit var prefs: SharedPreferences

  private lateinit var loadingLayout: ViewGroup
  private lateinit var loadingProgressBar: ProgressBar
  private lateinit var loginLayout: ViewGroup
  private lateinit var usernameInput: EditText
  private lateinit var pinInput: EditText
  private lateinit var loginIncorrect: TextView
  private lateinit var loginButton: Button
  private lateinit var clearAppDataButton: Button

  override fun onCreate(savedInstanceState: Bundle?) {
    logger.error("onCreate (recreating: {})", savedInstanceState != null)
    super.onCreate(savedInstanceState)
  }

  override fun onStart() {
    logger.error("onStart")
    super.onStart()
  }

  override fun onStop() {
    logger.error("onStop")
    super.onStop()
  }

  override fun onDestroyView() {
    logger.error("onDestroyView")
    super.onDestroyView()
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    logger.error("onViewCreated (recreating: {})", savedInstanceState != null)
    super.onViewCreated(view, savedInstanceState)

    if (!testLoginEnabled) {
      logger.error("Test login is disabled")
      DataUtil.restartApp(requireContext())
      return
    }

    prefs = requireContext().getSharedPreferences("EkirjastoTesting", MODE_PRIVATE)

    supportActionBar?.hide()

    loadingLayout = view.findViewById(R.id.testingLoadingLayout)
    loadingLayout.visibility = View.GONE
    loadingProgressBar = loadingLayout.findViewById(R.id.testingLoadingProgressBar)
    loginLayout = view.findViewById(R.id.testingLoginLayout)
    usernameInput = view.findViewById(R.id.testingUsernameInput)
    pinInput = view.findViewById(R.id.testingPinInput)
    loginIncorrect = view.findViewById(R.id.testingLoginIncorrect)
    loginIncorrect.visibility = View.INVISIBLE
    loginButton = view.findViewById(R.id.testingLoginButton)
    clearAppDataButton = view.findViewById(R.id.testingClearAppDataButton)
    configureUI()

    if (prefilledUsername.isEmpty()) {
      usernameInput.requestFocus()
    }
    else {
      usernameInput.setText(prefilledUsername, TextView.BufferType.EDITABLE)
      pinInput.requestFocus()
    }
  }

  /**
   * Configure the UI.
   */
  private fun configureUI() {
    usernameInput.setOnEditorActionListener { _, actionId, _ ->
      return@setOnEditorActionListener when (actionId) {
        EditorInfo.IME_ACTION_DONE -> {
          pinInput.requestFocus()
          true
        }
        else -> false
      }
    }
    pinInput.setOnEditorActionListener { _, actionId, _ ->
      return@setOnEditorActionListener when (actionId) {
        EditorInfo.IME_ACTION_DONE -> {
          checkLogin()
          true
        }
        else -> false
      }
    }

    loginButton.setOnClickListener { checkLogin() }
    clearAppDataButton.setOnClickListener { DataUtil.clearAppDataAndExit(requireContext()) }
  }

  private fun checkLogin() {
    logger.debug("checkLogin()")

    if (!testLoginEnabled) {
      logger.error("Test login is disabled")
      DataUtil.restartApp(requireContext())
      return
    }

    val inputUsername = usernameInput.text.toString()
    val inputPin = pinInput.text.toString()

    val correctUserName = BuildConfig.TEST_LOGIN_USERNAME
    val correctPin = BuildConfig.TEST_LOGIN_PIN_CODE
    if ((correctUserName.lowercase() == inputUsername.lowercase())
        && (correctPin.lowercase() == inputPin.lowercase())) {
      logger.info("Correct login")
      finishLogin()
    }
    else {
      logger.error("Incorrect login")
      loginIncorrect.visibility = View.VISIBLE
    }
  }
  
  private fun finishLogin() {
    logger.warn("finishLogin()")
    hideVirtualKeyboard()
    loadingLayout.visibility = View.VISIBLE
    loginLayout.visibility = View.GONE
    setTestLoginActive()
    DataUtil.deleteEverythingExceptSharedPrefs(requireContext())
    DataUtil.restartApp(requireContext())
  }

  private fun hideVirtualKeyboard() {
    logger.debug("hideVirtualKeyboard()")
    val imm = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(activity?.currentFocus?.windowToken, 0)
  }

  @SuppressLint("ApplySharedPref")
  private fun setTestLoginActive() {
    logger.info("setTestLoginActive()")
    val prefs = requireContext().getSharedPreferences("EkirjastoTesting", MODE_PRIVATE)
    val prefsEditor = prefs.edit()
    prefsEditor.putBoolean("testLoginActive", true)
    prefsEditor.commit()
    TestingOverrides.testLoginActive = true
  }
}
