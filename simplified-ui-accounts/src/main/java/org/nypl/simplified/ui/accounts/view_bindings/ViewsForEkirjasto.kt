package org.nypl.simplified.ui.accounts.view_bindings

import android.content.res.Resources
import android.text.InputType
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.textfield.TextInputEditText
import org.librarysimplified.ui.accounts.R
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.accounts.api.AccountUsername
import org.nypl.simplified.ui.accounts.AccountLoginButtonStatus
import org.nypl.simplified.ui.accounts.OnTextChangeListener
import org.nypl.simplified.ui.accounts.ekirjastosuomifi.EkirjastoLoginMethod
import org.slf4j.LoggerFactory

class ViewsForEkirjasto(
  override val viewGroup: ViewGroup,
  val suomifiLoginContainer: LinearLayout,
  val passkeyLoginContainer: LinearLayout,
  val cancelContainer: LinearLayout,
  val suomiFiButton: Button,
  val username: TextInputEditText,
  val passkeyLoginButton: Button,
  val passkeyRegisterButton : Button,
  val cancelLabel: TextView,
  val cancelButton: Button
) : AccountAuthenticationViewBindings() {

  private val logger = LoggerFactory.getLogger(ViewsForEkirjasto::class.java)

  private var activeLoginMethod : EkirjastoLoginMethod = EkirjastoLoginMethod.SuomiFi()

  var passkeyState : EkirjastoLoginMethod.Passkey.LoginState = EkirjastoLoginMethod.Passkey.LoginState.RegisterUnavailable


  private val tokenTextListener =
    OnTextChangeListener(
      onChanged = { _, _, _, _ ->
      }
    )

  init {
    this.username.addTextChangedListener(this.tokenTextListener)
  }

  override fun lock() {
    this.username.isEnabled = false
  }

  override fun unlock() {
    this.username.isEnabled = true
  }

  private fun handleLoginEnabled(status: AccountLoginButtonStatus.AsLoginButtonEnabled, res: Resources) {
    logger.warn("Handle Login Enabled")
//    this.suomifiLoginContainer.visibility = VISIBLE
//    this.cancelContainer.visibility = GONE
//    this.suomiFiButton.isEnabled = false
//    this.passkeyLoginButton.isEnabled = false
//    this.passkeyRegisterButton.isEnabled = false

    when (passkeyState) {
      EkirjastoLoginMethod.Passkey.LoginState.RegisterUnavailable -> {
        this.logger.debug("Ekirjasto passkey registering is not available")
        this.suomifiLoginContainer.visibility = VISIBLE
        this.passkeyLoginContainer.visibility = VISIBLE
        this.cancelContainer.visibility = GONE
        this.passkeyRegisterButton.visibility = GONE
        this.suomiFiButton.text = res.getString(R.string.accountLoginWith, "Suomi.fi")
        this.suomiFiButton.isEnabled = true
        this.suomiFiButton.setOnClickListener {
          this.activeLoginMethod = EkirjastoLoginMethod.SuomiFi()
          status.onClick.invoke()
        }
        this.passkeyLoginButton.text = res.getString(R.string.accountLoginWith, "passkey")
        this.passkeyLoginButton.setOnClickListener{
          this.passkeyState = EkirjastoLoginMethod.Passkey.LoginState.LoggingIn
          this.activeLoginMethod = EkirjastoLoginMethod.Passkey(
            loginState = this.passkeyState,
            username = getUsername(),
            circulationToken = null)
          status.onClick.invoke()
        }
      }
      EkirjastoLoginMethod.Passkey.LoginState.RegisterAvailable -> {
        this.logger.debug("Ekirjasto passkey register available")
        //suomiFiLogin is ignored because it is probably configured as logout button at this point
        this.suomifiLoginContainer.visibility = GONE
        this.passkeyLoginContainer.visibility = VISIBLE
        this.cancelContainer.visibility = VISIBLE
        this.passkeyLoginButton.visibility = GONE
        this.username.isEnabled = true
        this.passkeyRegisterButton.visibility = VISIBLE
        this.passkeyRegisterButton.setOnClickListener {
          this.activeLoginMethod = EkirjastoLoginMethod.Passkey(passkeyState, null, null)
          status.onClick.invoke()
        }
      }
      EkirjastoLoginMethod.Passkey.LoginState.LoggingIn -> {
        this.logger.debug("Ekirjasto passkey is Logging In")
        this.cancelContainer.visibility = VISIBLE
        this.suomifiLoginContainer.visibility = GONE
        this.passkeyLoginContainer.visibility = GONE
//        this.passkeyLoginButton.visibility = VISIBLE
//        this.passkeyRegisterButton.visibility = GONE
//        this.passkeyLoginButton.setOnClickListener {
//          this.activeLoginMethod = EkirjastoLoginMethod.Passkey(passkeyState, null, getUsername())
//          status.onClick.invoke()
//        }
      }
      EkirjastoLoginMethod.Passkey.LoginState.LoggedIn -> {
        this.cancelContainer.visibility = VISIBLE
        this.passkeyLoginContainer.visibility = GONE
        this.suomifiLoginContainer.visibility = GONE
        logger.warn("Already logged in with passkey. Should not have LoginButton enabled status!")
      }
    }
  }

  override fun setLoginButtonStatus(status: AccountLoginButtonStatus) {
    val res = this.viewGroup.resources
    logger.warn("Ekirjasto view: setLoginButtonStatus $status")
    when (status) {
      is AccountLoginButtonStatus.AsLoginButtonEnabled -> handleLoginEnabled(status,res)
      AccountLoginButtonStatus.AsLoginButtonDisabled -> {
        this.suomifiLoginContainer.visibility = GONE
        this.passkeyLoginButton.visibility = GONE
        this.cancelContainer.visibility = GONE
//        this.passkeyLoginButton.text = res.getString(R.string.accountLoginWith, "passkey")
//        this.passkeyLoginButton.isEnabled = false
      }
      is AccountLoginButtonStatus.AsCancelButtonEnabled -> {
        this.suomifiLoginContainer.visibility = GONE
        this.passkeyLoginContainer.visibility = GONE
        this.cancelContainer.visibility = VISIBLE

        when (this.activeLoginMethod) {
          is EkirjastoLoginMethod.SuomiFi -> {
            this.cancelLabel.text = res.getString(
              R.string.accountEkirjastoLoggingInLabel,
              "Suomi.fi"
            )
          }
          is EkirjastoLoginMethod.Passkey -> {
            this.cancelLabel.text = res.getString(
              R.string.accountEkirjastoLoggingInLabel,
              "passkey"
            )
          }
        }

        this.cancelButton.setText(R.string.accountCancel)
        this.cancelButton.isEnabled = true
        this.cancelButton.setOnClickListener {
          status.onClick.invoke()
        }
      }
      AccountLoginButtonStatus.AsCancelButtonDisabled -> {
        this.suomifiLoginContainer.visibility = GONE
        this.passkeyLoginContainer.visibility = GONE
        this.cancelContainer.visibility = VISIBLE

        when (this.activeLoginMethod) {
          is EkirjastoLoginMethod.SuomiFi -> {
            this.cancelLabel.text = res.getString(
              R.string.accountEkirjastoLoggingInLabel,
              "Suomi.fi"
            )
          }
          is EkirjastoLoginMethod.Passkey -> {
            this.cancelLabel.text = res.getString(
              R.string.accountEkirjastoLoggingInLabel,
              "passkey"
            )
          }
        }

        this.cancelButton.setText(R.string.accountCancel)
        this.cancelButton.isEnabled = false
      }
      is AccountLoginButtonStatus.AsLogoutButtonEnabled -> {
        this.suomifiLoginContainer.visibility = GONE
        this.passkeyLoginContainer.visibility = GONE
        this.cancelContainer.visibility = VISIBLE
        this.cancelLabel.text = res.getString(R.string.accountEkirjastoLoggedInLabel)
        this.cancelButton.setText(R.string.accountLogout)
        this.cancelButton.isEnabled = true
        this.cancelButton.setOnClickListener {
          status.onClick.invoke()
        }
      }
      AccountLoginButtonStatus.AsLogoutButtonDisabled -> {
        this.suomifiLoginContainer.visibility = GONE
        this.passkeyLoginContainer.visibility = GONE
        this.cancelContainer.visibility = VISIBLE

        this.cancelLabel.text = res.getString(R.string.accountEkirjastoLoggedInLabel)

        this.cancelButton.setText(R.string.accountLogout)
        this.cancelButton.isEnabled = false
      }
    }
    this.logger.warn("LoginButtonStatus Set. Login Method = ${this.activeLoginMethod}")
  }

  override fun setResetPasswordLabelStatus(
    status: AccountLoginButtonStatus,
    isVisible: Boolean,
    onClick: () -> Unit
  ) {}

  override fun blank() {
    this.setUsername("")
  }

  fun setUsername(
    token: String
  ) {
    this.username.setText(token, TextView.BufferType.EDITABLE)
  }

  fun isSatisfied(description: AccountProviderAuthenticationDescription.Ekirjasto): Boolean {
    return true
  }

  fun configureFor(description: AccountProviderAuthenticationDescription.Ekirjasto) {
    // Set input types
    this.logger.debug("Setting {} for token input type")
    this.username.inputType = InputType.TYPE_CLASS_TEXT
  }

  fun getUsername(): AccountUsername {
    return AccountUsername(this.username.text.toString().trim())
  }

  fun getActiveLoginMethod(): EkirjastoLoginMethod {
    return activeLoginMethod
  }

  fun updatePasskeyState(state: EkirjastoLoginMethod.Passkey.LoginState) {
    this.passkeyState = state
    when (val method = activeLoginMethod){
      is EkirjastoLoginMethod.Passkey -> {
        activeLoginMethod = EkirjastoLoginMethod.Passkey(
          loginState = state,
          circulationToken = method.circulationToken,
          username = method.username
        )
      }
      else -> return
    }

  }

  companion object {
    fun bind(
      viewGroup: ViewGroup
    ): ViewsForEkirjasto {
      return ViewsForEkirjasto(
        viewGroup = viewGroup,
        suomifiLoginContainer = viewGroup.findViewById(R.id.suomifiLoginContainer),
        passkeyLoginContainer = viewGroup.findViewById(R.id.passkeyLoginContainer),
        cancelContainer = viewGroup.findViewById(R.id.ekirjastoCancelContainer),
        suomiFiButton = viewGroup.findViewById(R.id.suomiFiLogin),
        username = viewGroup.findViewById(R.id.authPassKeyUsernameField),
        passkeyLoginButton = viewGroup.findViewById(R.id.passKeyLogin),
        passkeyRegisterButton = viewGroup.findViewById(R.id.passKeyRegister),
        cancelLabel = viewGroup.findViewById(R.id.ekirjastoCancelLabel),
        cancelButton = viewGroup.findViewById(R.id.ekirjastoCancelButton),
      )
    }
  }
}
