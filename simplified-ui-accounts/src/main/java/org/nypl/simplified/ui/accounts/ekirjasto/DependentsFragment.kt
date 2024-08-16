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

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    //Create the viewmodel
    this.viewModel = ViewModelProvider(this)[DependentsViewModel::class.java]
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    //Link all the view elements to their counterparts
    val spinner = view.findViewById<Spinner>(R.id.spinner)
    val emailInput = view.findViewById<EditText>(R.id.email)
    val send = view.findViewById<Button>(R.id.send)
    val buttonDependents = view.findViewById<Button>(R.id.buttonDependents)
    val dependentInfoText = view.findViewById<TextView>(R.id.dependentInfoText)
    val progressBar = view.findViewById<ProgressBar>(R.id.dependentsLoading)
    val moreDependentsButton = view.findViewById<Button>(R.id.invMoreDependents)

    //set elements to be invisible at start
    emailInput.visibility = GONE
    send.visibility = GONE
    dependentInfoText.visibility = GONE
    spinner.visibility = GONE
    progressBar.visibility = GONE
    moreDependentsButton.visibility = GONE

    //Handle click event of a get dependents button
    buttonDependents.setOnClickListener {
      logger.debug("Get Dependents Button Pressed!")
      //Call the viewmodel to handle getting the dependents from the server
      viewModel.lookupDependents()
      //Show progress bar while we are loading the dependents info
      buttonDependents.visibility = GONE
      progressBar.visibility = VISIBLE
    }

    //Add an adapter for programmatically adding spinner items when there are some
    val adapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item)
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    spinner.adapter = adapter

    //Observe the list of dependents and put them into the spinner and show the spinner when done
    //also stop showing the progress bar when there is something to show
    viewModel.dependentListLive.observe(viewLifecycleOwner) { dependents ->
      if (dependents.isNotEmpty()) {
        adapter.clear()
        adapter.add(getString(R.string.select_dependent))
        //adapter.add("Another Item for debugging purposes only")
        dependents.forEach { dependent ->
          adapter.add(dependent.firstName)
        }
        adapter.notifyDataSetChanged()
        //Show results
        spinner.visibility = VISIBLE
      } else {
        //User has no dependents so inform user
        dependentInfoText.text = getString(R.string.no_dependents)
        dependentInfoText.visibility = VISIBLE
        //Don't show spinner if no dependents
        spinner.visibility = GONE
      }
      //After loading we want to hide progress bar
      progressBar.visibility = GONE
      //After loading we want to show the dependents lookup button again
      buttonDependents.visibility = VISIBLE
    }

    //Observe possible error states that the user should be informed of
    viewModel.errorLive.observe(viewLifecycleOwner) { error ->
      //If there was an error or success, should hide spinner and loading
      spinner.visibility = GONE
      progressBar.visibility = GONE
      when (error) {
          "Success" -> {
            //Set thank you message and show it
            dependentInfoText.text = getString(R.string.thanks)
            dependentInfoText.visibility = VISIBLE
            //Show the invite another -button
            moreDependentsButton.visibility = VISIBLE
          }
          "Error" -> {
            //Error is from the server
            //Set and show error message
            dependentInfoText.text = getString(R.string.errorFromServer)
            dependentInfoText.visibility = VISIBLE
            //Show the get dependents button so user can try again
            buttonDependents.visibility = VISIBLE
            //Don't show invite more in case of a fail
            moreDependentsButton.visibility = GONE
          }
          else -> {
            //Error happened on this end
            //Set and show error message
            dependentInfoText.text = getString(R.string.errorInCreation)
            dependentInfoText.visibility = VISIBLE
            //Show the get dependents button so user can try again
            buttonDependents.visibility = VISIBLE
            //Don't show invite more in case of a fail
            moreDependentsButton.visibility = GONE
          }
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
        if (selectedItem == getString(R.string.select_dependent)) {
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

    moreDependentsButton.setOnClickListener {
      //Return the user to the spinner view

      //Hide this button
      moreDependentsButton.visibility = GONE

      //Show spinner, resetting it to first value
      spinner.setSelection(0)
      spinner.visibility = VISIBLE

      //Reset email field
      emailInput.text.clear()

      //Reset the info text
      dependentInfoText.visibility = GONE
      dependentInfoText.text = getString(R.string.guidetext)

      //Show get dependents button
      buttonDependents.visibility = VISIBLE
    }

    // handle click event of a send button
    send.setOnClickListener {
      logger.debug("Send Button Pressed!")

      //Getting the value user has put on the email text field and send it to debug
      val userInput: String = emailInput.text.toString()

      //validating user email, if valid, we post it
      if (isEmailValid(userInput)) {
        //hide everything except progress bar
        send.visibility = GONE
        emailInput.visibility = GONE
        spinner.visibility = GONE
        buttonDependents.visibility = GONE
        progressBar.visibility = VISIBLE
        dependentInfoText.visibility = GONE

        //Post the dependent info
        postDependent(selectedDependent,userInput)


      } else {
        //If email is not email, inform user to try with a valid one
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
