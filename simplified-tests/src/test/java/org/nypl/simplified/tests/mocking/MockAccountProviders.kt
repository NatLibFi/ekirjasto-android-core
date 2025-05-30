package org.nypl.simplified.tests.mocking

import android.content.Context
import com.google.common.base.Preconditions
import org.joda.time.DateTime
import org.mockito.Mockito
import org.nypl.simplified.accounts.api.AccountProvider
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.registry.AccountProviderRegistry
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.TreeMap

object MockAccountProviders {

  fun fakeProvider(
    providerId: String,
    host: String = "example.com",
    port: Int = 80
  ): AccountProvider {
    return AccountProvider(
      addAutomatically = false,
      announcements = emptyList(),
      authentication = AccountProviderAuthenticationDescription.Anonymous,
      authenticationAlternatives = listOf(),
      authenticationDocumentURI = null,
      cardCreatorURI = null,
      catalogURI = URI.create("http://$host:$port/accounts0/feed.xml"),
      description = "Fake Library description",
      displayName = "Fake Library",
      eula = null,
      id = URI.create(providerId),
      idNumeric = -1,
      isProduction = false,
      license = null,
      loansURI = URI.create("http://$host:$port/accounts0/loans.xml"),
      logo = URI.create("data:text/plain;base64,U3RvcCBsb29raW5nIGF0IG1lIQo="),
      mainColor = "#ff0000",
      patronSettingsURI = URI.create("http://$host:$port/accounts0/patrons/me"),
      privacyPolicy = null,
      resetPasswordURI = URI.create("http://$host:$port/reset-password"),
      subtitle = "Imaginary books",
      supportEmail = "postmaster@example.com",
      supportsReservations = false,
      updated = DateTime.parse("2000-01-01T00:00:00Z"),
      location = null,
      alternateURI = URI.create("https://www.example.com/alternate"),
      selectedURI = URI.create("http://$host:$port/accounts0/loans.xml"),
    )
  }

  fun fakeAccountProviderDefaultAutoURI(): URI {
    return URI.create("urn:fake:auto-4")
  }

  fun fakeAccountProviders(): AccountProviderRegistryType {
    val fake0 = fakeProvider("urn:fake:0")
    val fake1 = fakeProvider("urn:fake:1")
    val fake2 = fakeProvider("urn:fake:2")
    val fake3 = fakeAuthProvider("urn:fake-auth:0")

    val providers = TreeMap<URI, AccountProviderType>()
    providers[fake0.id] = fake0
    providers[fake1.id] = fake1
    providers[fake2.id] = fake2
    providers[fake3.id] = fake3

    val registry =
      AccountProviderRegistry.createFrom(Mockito.mock(Context::class.java), listOf(), fake0)

    for (provider in providers.values) {
      registry.updateProvider(provider)
    }

    return registry
  }

  fun fakeAccountProviderList(): List<AccountProviderType> {
    return listOf(
      fakeProvider("urn:fake:0"),
      fakeProvider("urn:fake:1"),
      fakeProvider("urn:fake:2"),
      fakeAuthProvider("urn:fake-auth:0")
    )
  }

  fun fakeAccountProviderListWithAutomatic(): List<AccountProviderType> {
    return listOf(
      fakeProvider("urn:fake:0"),
      fakeProvider("urn:fake:1"),
      fakeProvider("urn:fake:2"),
      fakeAuthProvider("urn:fake-auth:0"),
      fakeProviderAuto("urn:fake:auto-4")
    )
  }

  fun fakeProviderAuto(id: String): AccountProvider {
    return fakeProvider(id).copy(addAutomatically = true)
  }

  fun fakeAuthProvider(
    uri: String,
    host: String = "example.com",
    port: Int = 80
  ): AccountProvider {
    return fakeProvider(uri, host, port)
      .copy(
        authentication = AccountProviderAuthenticationDescription.Basic(
          barcodeFormat = "CODABAR",
          keyboard = AccountProviderAuthenticationDescription.KeyboardInput.DEFAULT,
          passwordMaximumLength = 4,
          passwordKeyboard = AccountProviderAuthenticationDescription.KeyboardInput.DEFAULT,
          description = "Stuff!",
          labels = mapOf(),
          logoURI = null
        )
      )
  }
}
