package org.nypl.simplified.ui.accounts.ekirjasto

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import fi.kansalliskirjasto.ekirjasto.util.FontSizeUtil
import fi.kansalliskirjasto.ekirjasto.util.LanguageUtil
import fi.kansalliskirjasto.ekirjasto.util.LocaleHelper
import org.librarysimplified.ui.accounts.R
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.listeners.api.fragmentListeners
import org.slf4j.LoggerFactory

class PreferencesFragment : Fragment(R.layout.account_resources) {
  fun create() : PreferencesFragment {
    val fragment = PreferencesFragment()
    return fragment
  }

  private val logger =
    LoggerFactory.getLogger(PreferencesFragment::class.java)
  private val listener: FragmentListenerType<TextSizeEvent> by fragmentListeners()

  private lateinit var buttonLanguage: Button
  private lateinit var buttonFontSize: Button
  private lateinit var switchPreferences: SwitchCompat

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    this.buttonLanguage = view.findViewById(R.id.buttonLanguage)
    this.buttonFontSize = view.findViewById(R.id.buttonFontSize)
    this.switchPreferences = view.findViewById(R.id.accountAllowPreferencesCheck)

    //If settings are different from the default, show the switch as checked
    if(LocaleHelper.getLanguage(this.requireContext()) != "fi" || FontSizeUtil(this.requireContext()).getFontSize() != 1.0f){
      switchPreferences.isChecked = true
    }
    //Have the buttons start in the same state as the switch
    this.buttonLanguage.isEnabled = switchPreferences.isChecked
    this.buttonFontSize.isEnabled = switchPreferences.isChecked
  }

  override fun onStart() {
    super.onStart()

    this.buttonLanguage.setOnClickListener {
      this.logger.debug("Language button clicked")
      languageOptions()
    }

    this.buttonFontSize.setOnClickListener {
      this.logger.debug("Text Button clicked")
      fontSizeOptions()
    }

    this.switchPreferences.setOnCheckedChangeListener { buttonView, isChecked ->
      if (isChecked) {
        this.buttonFontSize.isEnabled = true
        this.buttonLanguage.isEnabled = true
      } else {
        this.buttonFontSize.isEnabled = false
        this.buttonLanguage.isEnabled = false
      }
    }
  }
  //Show a an alert box with all language options
  private fun languageOptions() {
    // Build the dialog, using the translatable language names
    val alertBuilder = MaterialAlertDialogBuilder(this.requireContext())
    val languages : Array<String> = arrayOf(
      getString(R.string.buttonTextFinnish),
      getString(R.string.buttonTextSwedish),
      getString(R.string.buttonTextEnglish))
    val current = LanguageUtil.getUserLanguage(this.requireContext())
    logger.debug("Current language {}", current)
    var curr = -1
    when (current) {
      "fi" -> curr = 0
      "sv" -> curr = 1
      "en" -> curr = 2
    }
    alertBuilder.setTitle(R.string.account_application_language)
    alertBuilder.setSingleChoiceItems(languages, curr) { dialog, checked ->
      //if the language they choose is not the current one, show the confirmation popup
      if (checked != curr) {
        when (checked) {
          0 -> popUp("fi")
          1 -> popUp("sv")
          2-> popUp("en")
        }
      }
      dialog.dismiss()
    }
    alertBuilder.create().show()
  }

  // Show a popup asking if user wants to set chosen language
  private fun popUp (language: String) {
    logger.debug("Changing language to {}", language)
    val builder: AlertDialog.Builder = AlertDialog.Builder(this.requireContext())
    builder
      .setMessage(R.string.restartPopupMessage)
      .setTitle(R.string.restartPopupTitle)
      .setPositiveButton(R.string.restartPopupAgree) { dialog, which ->
        //Set locale to the wanted language to be used on restart
        LocaleHelper.setLocale(this.requireContext(), language)
      }
      .setNegativeButton(R.string.restartPopupCancel) { dialog, which ->
        //do nothing
      }

    val dialog: AlertDialog = builder.create()
    dialog.show()
  }

  private fun fontSizeOptions() {
    // Build the dialog, using the translatable language names
    val alertBuilder = MaterialAlertDialogBuilder(this.requireContext())
    val languages : Array<String> = arrayOf("100%", "150%", "200%")
    val current = 0
    logger.debug("Current choice {}", current)
    alertBuilder.setTitle("Text size")
    alertBuilder.setSingleChoiceItems(languages, current) { dialog, checked ->
      //if the language they choose is not the current one, show the confirmation popup

      when (checked) {
        0 -> listener.post(
          TextSizeEvent.TextSizeSmall
        )
        1 -> listener.post(
          TextSizeEvent.TextSizeMedium
        )
        2-> listener.post(
          TextSizeEvent.TextSizeLarge
        )
      }

      dialog.dismiss()
    }
    alertBuilder.create().show()
  }
}
