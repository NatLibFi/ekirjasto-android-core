package org.nypl.simplified.ui.accounts.ekirjasto

import android.os.Bundle
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import fi.kansalliskirjasto.ekirjasto.util.LanguageUtil
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.accounts.R
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.slf4j.LoggerFactory

class DependentsFragment : Fragment(R.layout.dependents) {
  //Viewmodel that handles get/post requests as well as updating dependent list
  private lateinit var viewModel: DependentsViewModel
  val logger = LoggerFactory.getLogger(DependentsFragment::class.java)

  companion object {
    private const val PATRON_ID = "org.nypl.simplified.ui.accounts.ekirjasto.patron"
    private const val ACCESS_TOKEN = "org.nypl.simplified.ui.accounts.ekirjasto.token"

    fun create(patron: String?, accessToken: String?) = DependentsFragment().apply {
      arguments = bundleOf(PATRON_ID to patron, ACCESS_TOKEN to accessToken)
    }
  }

  private val patron by lazy { arguments?.getString(PATRON_ID) }
  private val token by lazy { arguments?.getString(ACCESS_TOKEN) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    //Create the viewmodel
    this.viewModel = ViewModelProvider(this)[DependentsViewModel::class.java]
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    //Link all the view elements to their code counterparts
    val spinner = view.findViewById<Spinner>(R.id.spinner)
    val emailInput = view.findViewById<EditText>(R.id.email)
    val send = view.findViewById<Button>(R.id.send)
    val buttonDependents = view.findViewById<Button>(R.id.buttonDependents)
    val dependentInfoText = view.findViewById<TextView>(R.id.dependentInfoText)
    val progressBar = view.findViewById<ProgressBar>(R.id.dependentsLoading)

    //set elements to be invisible at start
    emailInput.visibility = GONE
    send.visibility = GONE
    dependentInfoText.visibility = GONE
    spinner.visibility = GONE
    progressBar.visibility = GONE

    //Handle click event of a get dependents button
    buttonDependents.setOnClickListener {
      logger.debug("Get Dependents Button Pressed!")
      //Call the viewmodel to handle getting the dependents from the server
      viewModel.lookupDependents()
      //Show progress bar while we are loading the dependents info
      progressBar.visibility = VISIBLE
    }

    //Add an adapter for programmatically adding spinner items when there are some
    val adapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item)
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    spinner.adapter = adapter

    //Observe the list of dependents and put them into the spinner and show the spinner when done
    //also stop showing the progress bar when there is something to show
    viewModel.dependentListLive.observe(viewLifecycleOwner) { depList ->
      if (depList.isNotEmpty()) {
        adapter.add("Start by Getting Dependents")
        //adapter.add("Another Item for debugging purposes only")
        depList.forEach { d ->
          adapter.add(d.firstName)
        }
        adapter.notifyDataSetChanged()
        progressBar.visibility = GONE
        spinner.visibility = VISIBLE
      } else {
        spinner.visibility = GONE
      }
    }

    //set state of selection boolean to false and send to debug console
    var selected = false
    var selectedDependent = ""
    logger.debug("State of selection is $selected")

    //Set up item selection listener
    spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val selectedItem = parent?.getItemAtPosition(position).toString()

        // Handle the selected item here and post selection to debug console
        logger.debug("Item Selected: $selectedItem")

        //Check if selected other than default value
        //and if so, set input field, info and send button visible
        if (selectedItem == "Start by Getting Dependents") {
          emailInput.visibility = GONE
          send.visibility = GONE
          dependentInfoText.visibility = GONE
          selected = false
        } else {
          selected = true
          logger.debug("State of selection is $selected")
          selectedDependent = selectedItem
          emailInput.visibility = VISIBLE
          send.visibility = VISIBLE
          dependentInfoText.visibility = VISIBLE
        }
      }

      //handle nothing selected
      override fun onNothingSelected(parent: AdapterView<*>?) {
        //no item selected, therefore user cannot continue
        //so we are hiding everything
        emailInput.visibility = GONE
        send.visibility = GONE
        dependentInfoText.visibility = GONE
      }
    }


    // handle click event of a send button
    send.setOnClickListener {
      logger.debug("Send Button Pressed!")

      //Getting the value user has put on the email text field and send it to debug
      val userInput: String = emailInput.text.toString()
      logger.debug(userInput)

      //validating user email
      if (isEmailValid(userInput)) {
        send.visibility = GONE
        emailInput.visibility = GONE
        spinner.visibility = GONE
        buttonDependents.visibility = GONE

        //Post the dependent info
        postDependent(selectedDependent,userInput)


        //reset email field
        emailInput.hint = getString(R.string.dependent_email)

        //set thank you message
        dependentInfoText.text = getString(R.string.thanks)
      } else {
        dependentInfoText.text = getString(R.string.emailNotValid)
      }
    }
  }

  //validate user input email
  private fun isEmailValid(userInput: String): Boolean {
    val emailRegex = "^[A-Za-z](.*)(@)(.+)(\\.)(.+)"
    return userInput.matches(emailRegex.toRegex())
  }

  //Trigger the post method
  private fun postDependent(dependentName: String, email: String) {
    logger.debug("Entered post method")
    //Get the users language and use it as the dependents language
    val lang = LanguageUtil.getUserLanguage(this.requireContext())
    viewModel.postDependent(dependentName, email, lang)
  }

}
