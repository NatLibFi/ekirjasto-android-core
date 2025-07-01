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
import org.nypl.simplified.android.ktx.supportActionBar
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.listeners.api.fragmentListeners
import org.nypl.simplified.ui.accounts.AccountDetailEvent
import org.slf4j.LoggerFactory
import org.thepalaceproject.theme.core.PalaceToolbar

class PreferencesFragment : Fragment(R.layout.account_resources) {
  fun create() : PreferencesFragment {
    val fragment = PreferencesFragment()
    return fragment
  }

  private val logger =
    LoggerFactory.getLogger(PreferencesFragment::class.java)
  private val listener: FragmentListenerType<TextSizeEvent> by fragmentListeners()
  private val navListener: FragmentListenerType<PreferencesEvent> by fragmentListeners()

  private lateinit var buttonLanguage: Button
  private lateinit var buttonFontSize: Button
  private lateinit var switchPreferences: SwitchCompat

  private lateinit var toolbar: PalaceToolbar
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    //Link the layout elements
    this.buttonLanguage = view.findViewById(R.id.buttonLanguage)
    this.buttonFontSize = view.findViewById(R.id.buttonFontSize)
    this.switchPreferences = view.findViewById(R.id.accountAllowPreferencesCheck)

    // Inherit the toolbar
    this.toolbar =
      view.rootView.findViewWithTag(PalaceToolbar.palaceToolbarName)
  }

  override fun onStart() {
    super.onStart()
    // Configure toolbar to keep links and texts up-to-date
    this.configureToolbar()

    this.buttonLanguage.setOnClickListener {
      this.logger.debug("Language button clicked")
      languageOptions()
    }

    this.buttonFontSize.setOnClickListener {
      this.logger.debug("Text Button clicked")
      fontSizeOptions()
    }

    //Change the availability of the settings buttons based on the switch
    this.switchPreferences.setOnCheckedChangeListener { buttonView, isChecked ->
      if (isChecked) {
        this.buttonFontSize.isEnabled = true
        this.buttonLanguage.isEnabled = true
      } else {
        this.buttonFontSize.isEnabled = false
        this.buttonLanguage.isEnabled = false
      }
    }

    //If settings are different from the default, show the switch as checked
    if(LocaleHelper.getLanguage(this.requireContext()) != "fi" || FontSizeUtil(this.requireContext()).getFontSize() != 1.0f){
      switchPreferences.isChecked = true
    }
    //Have the buttons start in the same state as the switch
    this.buttonLanguage.isEnabled = switchPreferences.isChecked
    this.buttonFontSize.isEnabled = switchPreferences.isChecked
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
    val builder = MaterialAlertDialogBuilder(this.requireContext())
    builder
      .setMessage(R.string.restartPopupMessage)
      .setTitle(R.string.restartPopupTitle)
      .setPositiveButton(R.string.restartPopupAgree) { dialog, which ->
        //Set locale to the wanted language to be used on restart
        LocaleHelper.setLocale(this.requireContext(), language)
        dialog.dismiss()
      }
      .setNegativeButton(R.string.restartPopupCancel) { dialog, which ->
        //do nothing
        dialog.dismiss()
      }

    val dialog: AlertDialog = builder.create()
    dialog.show()
  }

  //Show a popup with the text size options from 100 to 200
  private fun fontSizeOptions() {
    // Build the dialog
    val alertBuilder = MaterialAlertDialogBuilder(this.requireContext())
    val fontSizes : Array<String> = arrayOf("100%", "125%", "150%", "175%", "200%")
    //Set the ticked value based on the set font size
    val current = FontSizeUtil(this.requireContext()).getFontSize()
    var curr = -1

    when (current) {
      1.0f -> curr = 0
      1.25f -> curr = 1
      1.5f -> curr = 2
      1.75f -> curr = 3
      2.0f -> curr = 4
    }

    logger.debug("Current font size choice {}", current)
    alertBuilder.setTitle(R.string.fontSize)
    alertBuilder.setSingleChoiceItems(fontSizes, curr) { dialog, checked ->
      //if the size they choose is not the current one, trigger font change
      if (checked != curr) {
        when (checked) {
          0 -> listener.post(
            TextSizeEvent.TextSizeSmall
          )
          1 -> listener.post(
            TextSizeEvent.TextSizeMedium
          )
          2 -> listener.post(
            TextSizeEvent.TextSizeLarge
          )
          3 -> listener.post(
            TextSizeEvent.TextSizeExtraLarge
          )
          4 -> listener.post(
            TextSizeEvent.TextSizeExtraExtraLarge
          )
        }
      } else {
        //do nothing
      }
      dialog.dismiss()
    }
    alertBuilder.create().show()
  }

  //Configure the toolbar
  private fun configureToolbar() {
    //Get the action bar from parent
    val actionBar = this.supportActionBar ?: return
    //Show the bar on the toolbar
    actionBar.show()
    //Set the back arrow to take user up a level instead of into the catalog
    actionBar.setDisplayHomeAsUpEnabled(true)
    //Set text description to null, text is already provided by the toolbar
    actionBar.setHomeActionContentDescription(null)
    //Set the shown title as Settings
    actionBar.setTitle(R.string.AccountTitle)
    this.toolbar.setLogoOnClickListener {
      //Pressing the back button takes you to last fragment, or if there is not one, into settings
      this.navListener.post(PreferencesEvent.GoUpwards)
      logger.debug("Backbutton pressed")
    }
  }
}
