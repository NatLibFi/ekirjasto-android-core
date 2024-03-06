package org.nypl.simplified.ui.accounts.view_bindings

import android.text.InputType
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.textfield.TextInputEditText
import org.librarysimplified.ui.accounts.R
import org.nypl.simplified.accounts.api.AccountPassword
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.accounts.api.AccountUsername
import org.nypl.simplified.ui.accounts.AccountLoginButtonStatus
import org.nypl.simplified.ui.accounts.OnTextChangeListener
import org.slf4j.LoggerFactory

class ViewsForEkirjasto(
  override val viewGroup: ViewGroup,
  val loginContainer: LinearLayout,
  val cancelContainer: LinearLayout,
  val suomiFiButton: Button,
  val username: TextInputEditText,
  val onUsernameChangeListener: (AccountUsername, AccountPassword) -> Unit,
  val passkeyLoginButton: Button,
  val passkeyRegisterButton : Button,
  val cancelLabel: TextView,
  val cancelButton: Button
) : AccountAuthenticationViewBindings() {

  enum class LoginMethod { SuomiFi, Passkey }

  private val logger = LoggerFactory.getLogger(ViewsForEkirjasto::class.java)

  private var activeLoginMethod = LoginMethod.SuomiFi

  private val tokenTextListener =
    OnTextChangeListener(
      onChanged = { _, _, _, _ ->
        this.onUsernameChangeListener.invoke(
          AccountUsername(this.username.text.toString()),
          AccountPassword("")
        )
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

  override fun setLoginButtonStatus(status: AccountLoginButtonStatus) {
    val res = this.viewGroup.resources
    logger.debug("setLoginButtonStatus $status")
    return when (status) {
      is AccountLoginButtonStatus.AsLoginButtonEnabled -> {
        this.loginContainer.visibility = VISIBLE
        this.cancelContainer.visibility = GONE

        this.passkeyLoginButton.text = res.getString(R.string.accountLoginWith, "passkey")
        this.passkeyLoginButton.isEnabled = true
        this.passkeyLoginButton.setOnClickListener {
          this.activeLoginMethod = LoginMethod.Passkey
          status.onClick.invoke()
        }
        this.suomiFiButton.text = res.getString(R.string.accountLoginWith, "Suomi.fi")
        this.suomiFiButton.isEnabled = true
        this.suomiFiButton.setOnClickListener {
          this.activeLoginMethod = LoginMethod.SuomiFi
          status.onClick.invoke()
        }
      }
      AccountLoginButtonStatus.AsLoginButtonDisabled -> {
        this.loginContainer.visibility = VISIBLE
        this.cancelContainer.visibility = GONE

        //this.suomiFiButton.text = res.getString(R.string.accountLoginWith, "Suomi.fi")
        //this.suomiFiButton.isEnabled = false
        this.passkeyLoginButton.text = res.getString(R.string.accountLoginWith, "passkey")
        this.passkeyLoginButton.isEnabled = false
      }
      is AccountLoginButtonStatus.AsCancelButtonEnabled -> {
        this.loginContainer.visibility = GONE
        this.cancelContainer.visibility = VISIBLE

        when (this.activeLoginMethod) {
          LoginMethod.SuomiFi -> {
            this.cancelLabel.text = res.getString(
              R.string.accountEkirjastoLoggingInLabel,
              "Suomi.fi"
            )
          }
          LoginMethod.Passkey -> {
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
        this.loginContainer.visibility = GONE
        this.cancelContainer.visibility = VISIBLE

        when (this.activeLoginMethod) {
          LoginMethod.SuomiFi -> {
            this.cancelLabel.text = res.getString(
              R.string.accountEkirjastoLoggingInLabel,
              "Suomi.fi"
            )
          }
          LoginMethod.Passkey -> {
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
        this.loginContainer.visibility = GONE
        this.cancelContainer.visibility = VISIBLE

        this.cancelLabel.text = res.getString(R.string.accountEkirjastoLoggedInLabel)

        this.cancelButton.setText(R.string.accountLogout)
        this.cancelButton.isEnabled = true
        this.cancelButton.setOnClickListener {
          status.onClick.invoke()
        }
      }
      AccountLoginButtonStatus.AsLogoutButtonDisabled -> {
        this.loginContainer.visibility = GONE
        this.cancelContainer.visibility = VISIBLE

        this.cancelLabel.text = res.getString(R.string.accountEkirjastoLoggedInLabel)

        this.cancelButton.setText(R.string.accountLogout)
        this.cancelButton.isEnabled = false
      }
    }
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
    return !username.text.isNullOrBlank()
  }

  fun configureFor(description: AccountProviderAuthenticationDescription.Ekirjasto) {
    // Set input types
    this.logger.debug("Setting {} for token input type")
    this.username.inputType = InputType.TYPE_CLASS_TEXT
  }

  fun getUsername(): AccountUsername {
    return AccountUsername(this.username.text.toString().trim())
  }

  fun getActiveLoginMethod(): LoginMethod {
    return activeLoginMethod
  }

  companion object {
    fun bind(
      viewGroup: ViewGroup,
      onUsernameChangeListener: (AccountUsername, AccountPassword) -> Unit
    ): ViewsForEkirjasto {
      return ViewsForEkirjasto(
        viewGroup = viewGroup,
        loginContainer = viewGroup.findViewById(R.id.ekirjastoLoginContainer),
        cancelContainer = viewGroup.findViewById(R.id.ekirjastoCancelContainer),
        suomiFiButton = viewGroup.findViewById(R.id.suomiFiLogin),
        username = viewGroup.findViewById(R.id.authPassKeyUsernameField),
        onUsernameChangeListener = onUsernameChangeListener,
        passkeyLoginButton = viewGroup.findViewById(R.id.passKeyLogin),
        passkeyRegisterButton = viewGroup.findViewById(R.id.passKeyRegister),
        cancelLabel = viewGroup.findViewById(R.id.ekirjastoCancelLabel),
        cancelButton = viewGroup.findViewById(R.id.ekirjastoCancelButton),
      )
    }
  }
}
