package org.nypl.simplified.ui.accounts.ekirjasto

import android.os.Bundle
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.accounts.R
import org.nypl.simplified.profiles.controller.api.ProfileDependentsLookupRequest
import org.nypl.simplified.profiles.controller.api.ProfileDependentsPostRequest
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.slf4j.LoggerFactory

class DependentsFragment : Fragment(R.layout.dependents) {
  // Get the service and the profile controller
  // into which I put the new function, since profiles handles profile changes
  // might need its own controller
  val services =
    Services.serviceDirectory()
  val profilesController =
    services.requireService(ProfilesControllerType::class.java)

  companion object {
    private const val PATRON_ID = "org.nypl.simplified.ui.accounts.ekirjasto.patron"
    private const val ACCESS_TOKEN = "org.nypl.simplified.ui.accounts.ekirjasto.token"

    fun create(patron: String?, accessToken: String?) = DependentsFragment().apply {
      arguments = bundleOf(PATRON_ID to patron, ACCESS_TOKEN to accessToken)
    }
  }

  private val patron by lazy { arguments?.getString(PATRON_ID) }
  private val token by lazy { arguments?.getString(ACCESS_TOKEN) }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val logger = LoggerFactory.getLogger(EKirjastoAccountFragment::class.java)
    val spinner = view.findViewById<Spinner>(R.id.spinner)
    val emailInput = view.findViewById<EditText>(R.id.email)
    val send = view.findViewById<Button>(R.id.send)
    val buttonDependents = view.findViewById<Button>(R.id.buttonDependents)
    val dependentInfoText = view.findViewById<TextView>(R.id.dependentInfoText)

    //set elements to be invisible at start
    emailInput.visibility = GONE
    send.visibility = GONE
    dependentInfoText.visibility = GONE
    spinner.visibility = GONE

    //Handle click event of a get dependents button
    buttonDependents.setOnClickListener {
      logger.debug("Get Dependents Button Pressed!")
      spinner.visibility = VISIBLE

      //Do the lookup, not the most simple or pretty way, but
      //Had struggle with error from networking in main thread so this got past it
      profilesController.profileDependentsLookup(
        ProfileDependentsLookupRequest.Ekirjasto(
          patronInfo = patron!!,
          ekirjastoToken = token!!
        )
      )

      val testValue = "testValue"
      val response = getDependents(testValue)
      logger.debug("Response is: $response")
    }

    val adapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item)
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    spinner.adapter = adapter

    // Add items to the spinner programmatically
    adapter.add("Start by Getting Dependents")
    adapter.add("Another Item for debugging purposes only")

    //adapter.remove("Start by Getting Dependents")

    adapter.notifyDataSetChanged()

    //add an adapter for programmatically add spinner items
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    spinner.adapter = adapter

    //set state of selection -boolean to false and send to debug console
    var selected = false
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

        //todo: call post method here
        logger.debug("Entered post method")


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

  //todo: get method
  private fun getDependents(testValue: String): String {
    val logger = LoggerFactory.getLogger(EKirjastoAccountFragment::class.java)
    logger.debug("Entered get method")
    //Trigger the post method
    profilesController.profileDependentsPost(
      ProfileDependentsPostRequest.Ekirjasto(
        ekirjastoToken = token!!,
        dependent = "Dependent" //change to actual dependent
      )
      /* Server wants the info of a dependent in this form. Role is always customer
firstname, lastname, govID can be found in the get infromation
  {
  locale: "fi",
  firstName: "Hulianna Katruska",
  lastName: "",
  govId: "140319*****",
  email: "test@example.com",
  role: "customer",
  }

  you can form an object like one above like so:
  data class User(
  val locale: String,
  val firstName: String,
  val lastName: String,
  val govId: String,
  val email: String,
  val role: String
  )

  and then

  create a new instance of the user data class with content

  val user = User(
  locale: "fi",
  firstName: "Hulianna Katruska",
  lastName: "",
  govId: "140319*****",
  email: "test@example.com",
  role: "customer"

  and then
  logger.debug(user)

  to read object into variables we could destructure the user object into variables here:

  val (locale, firstName, lastName, govId, email, role) = user
  no you can use all values as variable like so:

  logger.debug("Info to print: $variable to use")
  for example this way:
  logger.debug("Your govId is: $govId")

   */

    )


    val response = "none"
    return response
  }
  //todo: post method

}
