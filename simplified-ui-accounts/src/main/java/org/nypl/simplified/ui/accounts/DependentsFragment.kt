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
import androidx.fragment.app.Fragment
import org.librarysimplified.ui.accounts.R
import org.slf4j.LoggerFactory

class NewFragment : Fragment(R.layout.dependents) {
  fun create(): NewFragment {
    val dependentsFragment = NewFragment()
    return dependentsFragment
  }

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
      //todo: call get method here
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


    val response = "none"
    return response
  }
  //todo: post method

}
