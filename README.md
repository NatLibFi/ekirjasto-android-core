E-kirjasto Android core
=======================

The National Library of Finland's fork of the Palace Project Android client,
which is itself the Lyrasis fork of the NYPL's [Library Simplified](http://www.librarysimplified.org/) Android client.

![simplified](./src/site/resources/simplified.jpg?raw=true)

_Image by [Predrag Kezic](https://pixabay.com/users/PredragKezic-582203/?utm_source=link-attribution&utm_medium=referral&utm_campaign=image&utm_content=581229) from [Pixabay](https://pixabay.com/?utm_source=link-attribution&utm_medium=referral&utm_campaign=image&utm_content=581229)_

## What Is This?

The contents of this repository provide the E-kirjasto Android client application.

|Application|Module|Description|
|-----------|------|-----------|
|E-kirjasto|[simplified-app-ekirjasto](simplified-app-ekirjasto)|The DRM-enabled application|

The (mostly) original Palace Project app is under `simplified-app-palace`,
but it's only there for reference, and it's not used in any way for E-kirjasto.

## Contents

* [Building](#building-the-code)
  * [Android SDK](#android-sdk)
  * [JDK](#jdk)
  * [APK Signing](#apk-signing)
  * [Enabling DRM](#enabling-drm)
  * [LCP DRM Support](#lcp-drm-support)
  * [Test login](#test-login)
* [Development](#development)
  * [Branching/Merging](#branchingmerging)
  * [Project Structure](#project-structure--architecture)
    * [MVC](#mvc)
    * [MVVM](#mvvm)
    * [API vs SPI](#api-vs-spi)
    * [Modules](#modules)
  * [Binaries](#binaries)
  * [Ktlint](#ktlint)
* [Release Process](#release-process)
* [License](#license)

## Building The Code

The short version is to clone the repository recursively (including submodules),
then copy `local.properties.example` to `local.properties`,
and fill in the correct values.

### Cloning the Repository

Make sure you clone this repository with `git clone --recursive`. 
If you forgot to use `--recursive`, then execute:

```
$ git submodule update --init
```

### Android SDK

Install the [Android SDK and Android Studio](https://developer.android.com/studio/). We don't
support the use of any other IDE at the moment.

### JDK

Install a reasonably modern JDK, at least JDK 17. We don't recommend building
on anything newer than the current LTS JDK for everyday usage.

Any of the following JDKs should work:

  * [OpenJDK](https://jdk.java.net/java-se-ri/17)
  * [Adoptium](https://adoptopenjdk.net/)
  * [Amazon Coretto](https://aws.amazon.com/corretto/)
  * [Zulu](https://www.azul.com/downloads/zulu-community/?package=jdk)

The `JAVA_HOME` environment variable must be set correctly. You can check what it is set to in
most shells with `echo $JAVA_HOME`. If that command does not show anything, adding the following
line to `$HOME/.profile` and then executing `source $HOME/.profile` or opening a new shell
should suffice:  
```
# Replace NNN with your particular version of 17.
export JAVA_HOME=/path/to/jdk-17+NNN
```

You can verify that everything is set up correctly by inspecting the results of both
`java -version` and `javac -version`:  
```
$ java -version
openjdk version "17.0.8" 2023-07-18
OpenJDK Runtime Environment (build 17.0.8+7)
OpenJDK 64-Bit Server VM (build 17.0.8+7, mixed mode)
```

### APK signing

If you wish to generate a signed APK for publishing the application, you will need to copy
a keystore to `release.jks` and set the following values correctly in
`local.properties`:  
```
# Replace STOREPASSWORD, KEYPASSWORD, and KEYALIAS  appropriately.
# Do NOT use quotes around values.
ekirjasto.storePassword=STOREPASSWORD
ekirjasto.keyPassword=KEYPASSWORD
ekirjasto.keyAlias=KEYALIAS
```

Note that APK files are only signed with the release keystore if the code is
built in _release_ mode (debug builds are signed with `debug.keystore` that's
included in the repository). In other words, you need to use either of these
commands to produce signed release APK files:  
```
$ ./gradlew clean assembleRelease test
$ ./gradlew clean assemble test
```

### Enabling DRM

Unlike the Palace Project, E-kirjasto does not use Adobe DRM.
Instead, E-kirjasto only uses Readium LCP (liblcp) for content DRM.

### LCP DRM Support

The repository uses the test AAR for liblcp by default.

In order to build the app using the production version of liblcp,
copy the following to local.properties and replace "test" with the correct
production AAR path:  
```
ekirjasto.liblcp.repositorylayout=/[organisation]/[module]/android/aar/test/[revision].[ext]
```

### Test login

The app only allows logging in with strong authencation using Suomi.fi.
For development, a test version of the Suomi.fi login is used,
but reviewers need to be able to login into the production version of the app.

To allow this, the app has a test login accessible using a deep link.
First, set values like these in `local.properties`:  
```
ekirjasto.testLogin.enabled=true
ekirjasto.testLogin.username=TestUser
ekirjasto.testLogin.pin.base64=MTIzNDU2Nzg5MA==
```

Then, after closing the app (it cannot be open in the background),
you can access the test login page using this deep link:  
- ekirjasto://test-login

After inputting the test login credentials you defined in `local.properties`,
the app will switch to the development backend and restart the app.
This will allow Google Play app reviewers to use the production app,
without using the production servers or the production Suomi.fi login.

## Development

### Branching

`main` is the main development branch, and is only updated through pull requests.

Release branch names follow the convention: `release/<version>` (e.g. `release/1.2.3`).

### Continuous integration (CI)

The repository uses continuous integration to aid development and to automate releases.

See [.github/workflows/README.md] for more information about the CI workflows.

## Releasing

Please see [RELEASING.md](RELEASING.md) for documentation on E-kirjasto's release process.

### Project Structure / Architecture

#### MVC

The project, as a whole, roughly follows an [MVC](https://en.wikipedia.org/wiki/Model%E2%80%93view%E2%80%93controller)
architecture distributed over the application modules. The _controller_ in the application is
task-based and executes all tasks on a background thread to avoid any possibility of blocking
the Android UI thread.

#### MVVM

Newer application modules, roughly follow an [MVVM](https://en.wikipedia.org/wiki/Model%E2%80%93view%E2%80%93viewmodel) architecture.
The _View Model_ in the application exposes reactive properties
and executes all tasks on a background thread. The _View_ observes those properties and updates on the Android UI thread.

#### API vs SPI

The project makes various references to _APIs_ and _SPIs_. _API_ stands for _application
programming interface_ and _SPI_ stands for _service provider interface_.

An _API_ module defines a user-visible contract (or _specification_) for a module; it defines the
data types and abstract interfaces via which the user is expected to make calls in order to make use of a
module. An API module is typically paired with an _implementation_ module that provides concrete
implementations of the API interface types. A good example of this is the accounts database: The
[Accounts database API](simplified-accounts-database-api) declares a set of data types and
interfaces that describe how an accounts database should behave. The [Accounts database](simplified-accounts-database)
_implementation_ module provides an implementation of the described API. Keeping the API
_specification_ strictly separated from the _implementation_ in this manner has a number of benefits:

* Substitutability: When an _API_ has a sufficiently detailed specification, it's possible to
  replace an implementation module with a superior implementation without having to modify
  code that makes calls to the API.

* Testability: Keeping API types strictly separated from implementation types tends to lead to
  interfaces that are easy to mock.

* Understandability: Users of modules can go straight to the _API_ specifications to find out
  how to use them. This cuts down on the amount of archaeological work necessary to learn how
  to use the application's internal interfaces.

An _SPI_ module is similar to an API in that it provides a specification, however the defined
interfaces are expected to be _implemented_ by users rather than _called_ by users directly. An
implementor of an SPI is known as a _service provider_.

A good example of an SPI is the [Account provider source SPI](simplified-accounts-source-spi); the SPI
defines an interface that is expected to be implemented by account provider sources. The
[file-based source](simplified-accounts-source-filebased) module is capable of delivering account
provider descriptions from a bundled asset file. The [registry source](simplified-accounts-source-nyplregistry)
implementation is capable of fetching account provider descriptions from the NYPL's registry
server. Neither the _SPI_ or the implementation modules are expected to be used by application
programmers directly: Instead, implementation modules are loaded using [ServiceLoader](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/ServiceLoader.html)
by the [Account provider registry](simplified-accounts-registry), and users interact with the
registry via a [published registry API](simplified-accounts-registry-api). This same design
pattern is used by the [NYPL AudioBook API](https://github.com/NYPL-Simplified/audiobook-android)
to provide a common API into which new audio book players and parsers can be introduced _without
needing to modify application code at all_.

Modules should make every attempt not to specify explicit dependencies on _implementation_ modules.
API and implementation modules should typically only depend on other API modules, leaving the choice
of implementation modules to the final application assembly. In other words, a module should say
"I can work with any module that provides this API" rather than "I depend on implementation `M`
of a particular API". Following this convention allows us to replace module implementation without
having to modify lots of different parts of the application; it allows us to avoid
_strong coupling_ between modules.

Most of the modularity concepts described here were pioneered by the [OSGi module system](https://www.osgi.org/developer/modularity/)
and so, although the Library Simplified application is not an OSGi application, much of the
design and architecture conforms to conventions followed by OSGi applications. Further reading
can be found on the OSGi web site.

#### Build System

**NOTE:**  
This section is partially outdated. Version numbers are defined in the ekirjasto-android-platform repository.

The build is driven by the [build.gradle.kts](build.gradle.kts) file in the root of the project,
with the `build.gradle.kts` files in each module typically only listing dependencies (the actual
dependency definitions are defined in the root `build.gradle.kts` file to avoid duplicating version
numbers over the whole project). Metadata used to publish builds (such as Maven group IDs, version
numbers, etc) is defined in the `gradle.properties` file in each module. The [gradle.properties](gradle.properties)
file in the root of the project defines default values that are overridden as necessary by each
module.

#### Localization

The app uses [Transifex](https://github.com/transifex/transifex-java) for localizations.
Transifex overrides Android's default localization system (by overriding `getString()` etc.),
and loads localized strings from `txstrings.json` files in the app's assets folder.
These files are generated by the Transifex CLI tool and *should not be modified manually*.

The Transifex token is needed at runtime for release builds, but local translations
will work without it (the token will be replaced by an empty string).
The token should be placed in `local.properties` with the following line:  
`transifex.token=...`

##### Transifex command line tool

The main CI build workflow automatically uploads new strings to Transifex.
So, every time a PR is merged to the main branch,
all new localizations are pushed to Transifex.

To manually upload strings for translation or to download translated strings,
the `scripts/transifex.sh` wrapper script should be used.

Uploading strings for translation requires both the Transifex token and secret,
while downloading already translated strings only requires the token:
- The Transifex token is read from the `local.properties` file mentioned above.
    - Alternatively, the `TRANSIFEX_TOKEN` environment variable can be used.
- The Transifex token is also read from the `local.properties` file.
    - Set the secret as `transifex.secret=...` in `local.properties`.
    - Alternatively, the `TRANSIFEX_TOKEN` environment variable can be used.
    - If the secret is not given, uploading strings for translation will be skipped.

If the token and secret are in place in `local.properties`, just run:  
```
./scripts/transifex.sh
```

Or optionally use environment variables to specify the token and secret:  
```
TRANSIFEX_TOKEN="..." TRANSIFEX_SECRET="..." ./scripts/transifex.sh
```

#### Test suite

We aggregate all unit tests in the [simplified-tests](simplified-tests) module. Tests should
be written using the JUnit 5 library, although at the time of writing we have [one test](simplified-tests/src/test/java/org/nypl/simplified/tests/webview/CookiesContract.kt)
that still requires JUnit 4 due to the use of [Robolectric](http://robolectric.org/).

#### Modules

The project is heavily modularized in order to keep the separate application components as loosely
coupled as possible. New features should typically be implemented as new modules.

| Module                                                                                        |Description|
|-----------------------------------------------------------------------------------------------|-----------|
| [fi.kansalliskirjasto.ekirjasto.magazines](simplified-ekirjasto-magazines)                    |Digital magazines|
| [fi.kansalliskirjasto.ekirjasto.testing](simplified-ekirjasto-testing)                        |App testing functionality|
| [fi.kansalliskirjasto.ekirjasto.testing.ui](simplified-ekirjasto-testing-ui)                  |App testing UI components|
| [fi.kansalliskirjasto.ekirjasto.util](simplified-ekirjasto-util)                              |General utilities|
| [org.librarysimplified.accessibility](simplified-accessibility)                               |Accessibility APIs and functionality|
| [org.librarysimplified.accounts.api](simplified-accounts-api)                                 |Accounts API|
| [org.librarysimplified.accounts.database](simplified-accounts-database)                       |Accounts database implementation|
| [org.librarysimplified.accounts.database.api](simplified-accounts-database-api)               |Accounts database API|
| [org.librarysimplified.accounts.json](simplified-accounts-json)                               |Shared JSON classes|
| [org.librarysimplified.accounts.registry](simplified-accounts-registry)                       |Account provider registry implementation|
| [org.librarysimplified.accounts.registry.api](simplified-accounts-registry-api)               |Account provider registry API|
| [org.librarysimplified.accounts.source.filebased](simplified-accounts-source-filebased)       |File/asset based registry source implementation|
| [org.librarysimplified.accounts.source.nyplregistry](simplified-accounts-source-nyplregistry) |NYPL registry client implementation|
| [org.librarysimplified.accounts.source.spi](simplified-accounts-source-spi)                   |Account provider source SPI|
| [org.librarysimplified.adobe.extensions](simplified-adobe-extensions)                         |Adobe DRM convenience functions|
| [org.librarysimplified.analytics.api](simplified-analytics-api)                               |Analytics API|
| [org.librarysimplified.analytics.circulation](simplified-analytics-circulation)               |Circulation manager analytics implementation|
| [org.librarysimplified.android.ktx](simplified-android-ktx)                                   |Kotlin Android Extensions|
| [org.librarysimplified.announcements](simplified-announcements)                               |Announcements API|
| [org.thepalaceproject.palace](simplified-app-palace)                                          |Palace|
| [org.librarysimplified.books.api](simplified-books-api)                                       |Book types|
| [org.librarysimplified.books.audio](simplified-books-audio)                                   |Audio book support code|
| [org.librarysimplified.books.borrowing](simplified-books-borrowing)                           |Book borrowing|
| [org.librarysimplified.books.bundled.api](simplified-books-bundled-api)                       |Bundled books API|
| [org.librarysimplified.books.controller](simplified-books-controller)                         |Books/Profiles controller implementation|
| [org.librarysimplified.books.controller.api](simplified-books-controller-api)                 |Books controller API|
| [org.librarysimplified.books.covers](simplified-books-covers)                                 |Book cover loading and generation|
| [org.librarysimplified.books.database](simplified-books-database)                             |Book database implementation|
| [org.librarysimplified.books.database.api](simplified-books-database-api)                     |Book database API|
| [org.librarysimplified.books.formats](simplified-books-formats)                               |Book formats implementation|
| [org.librarysimplified.books.formats.api](simplified-books-formats-api)                       |Book formats API|
| [org.librarysimplified.books.preview](simplified-books-preview)                               |Book preview API|
| [org.librarysimplified.books.registry.api](simplified-books-registry-api)                     |Book registry API|
| [org.librarysimplified.boot.api](simplified-boot-api)                                         |Application boot API|
| [org.librarysimplified.buildconfig.api](simplified-buildconfig-api)                           |Build-time configuration API|
| [org.librarysimplified.cardcreator](simplified-cardcreator)                                   |NYPL card creator|
| [org.librarysimplified.content.api](simplified-content-api)                                   |Content resolver API|
| [org.librarysimplified.crashlytics](simplified-crashlytics)                                   |Crashlytics|
| [org.librarysimplified.crashlytics.api](simplified-crashlytics-api)                           |Crashlytics functionality|
| [org.librarysimplified.deeplinks.controller.api](simplified-deeplinks-controller-api)         |Deep Links|
| [org.librarysimplified.documents](simplified-documents)                                       |Documents|
| [org.librarysimplified.feeds.api](simplified-feeds-api)                                       |Feed API|
| [org.librarysimplified.files](simplified-files)                                               |File utilities|
| [org.librarysimplified.futures](simplified-futures)                                           |Guava Future extensions|
| [org.librarysimplified.json.core](simplified-json-core)                                       |JSON utilities|
| [org.librarysimplified.lcp](simplified-lcp)                                                   |LCP content protection provider|
| [org.librarysimplified.links](simplified-links)                                               |Link types|
| [org.librarysimplified.links.json](simplified-links-json)                                     |Link JSON parsing|
| [org.librarysimplified.main](simplified-main)                                                 |Main application module|
| [org.librarysimplified.metrics](simplified-metrics)                                           |Metrics|
| [org.librarysimplified.metrics](simplified-metrics-api)                                       |Metrics|
| [org.librarysimplified.migration.api](simplified-migration-api)                               |Data migration API|
| [org.librarysimplified.migration.spi](simplified-migration-spi)                               |Data migration SPI|
| [org.librarysimplified.networkconnectivity](simplified-networkconnectivity)                   |Network connectivity|
| [org.librarysimplified.networkconnectivity.api](simplified-networkconnectivity-api)           |Network connectivity API|
| [org.librarysimplified.notifications](simplified-notifications)                               |Notification service|
| [org.librarysimplified.oauth](simplified-oauth)                                               |OAuth|
| [org.librarysimplified.opds.auth_document](simplified-opds-auth-document)                     |OPDS authentication document parser implementation|
| [org.librarysimplified.opds.auth_document.api](simplified-opds-auth-document-api)             |OPDS authentication document parser API|
| [org.librarysimplified.opds.core](simplified-opds-core)                                       |OPDS feed parser|
| [org.librarysimplified.opds2](simplified-opds2)                                               |OPDS 2.0 model definitions|
| [org.librarysimplified.opds2.irradia](simplified-opds2-irradia)                               |OPDS 2.0 Parser [Irradia]|
| [org.librarysimplified.opds2.parser.api](simplified-opds2-parser-api)                         |OPDS 2.0 parser API|
| [org.librarysimplified.opds2.r2](simplified-opds2-r2)                                         |OPDS 2.0 Parser [R2]|
| [org.librarysimplified.parser.api](simplified-parser-api)                                     |Parser API|
| [org.librarysimplified.patron](simplified-patron)                                             |Patron user profile parser implementation|
| [org.librarysimplified.patron.api](simplified-patron-api)                                     |Patron user profile parser API|
| [org.librarysimplified.presentableerror.api](simplified-presentableerror-api)                 |Presentable error API|
| [org.librarysimplified.profiles](simplified-profiles)                                         |Profile database implementation|
| [org.librarysimplified.profiles.api](simplified-profiles-api)                                 |Profile database API|
| [org.librarysimplified.profiles.controller.api](simplified-profiles-controller-api)           |Profile controller API|
| [org.librarysimplified.reader.api](simplified-reader-api)                                     |Reader API types|
| [org.librarysimplified.reader.bookmarks](simplified-reader-bookmarks)                         |Reader bookmark service implementation|
| [org.librarysimplified.reader.bookmarks.api](simplified-reader-bookmarks-api)                 |Reader bookmark service API|
| [org.librarysimplified.reports](simplified-reports)                                           |Error reporting|
| [org.librarysimplified.services.api](simplified-services-api)                                 |Application services API|
| [org.librarysimplified.taskrecorder.api](simplified-taskrecorder-api)                         |Task recorder API|
| [org.librarysimplified.tenprint](simplified-tenprint)                                         |10PRINT implementation|
| [org.librarysimplified.tests](simplified-tests)                                               |Test suite|
| [org.librarysimplified.tests.sandbox](simplified-tests-sandbox)                               |Sandbox for informal testing|
| [org.librarysimplified.threads](simplified-threads)                                           |Thread utilities|
| [org.librarysimplified.ui.accounts](simplified-ui-accounts)                                   |Accounts UI components|
| [org.librarysimplified.ui.announcements](simplified-ui-announcements)                         |Announcements UI components|
| [org.librarysimplified.ui.branding](simplified-ui-branding)                                   |Branding functionality|
| [org.librarysimplified.ui.catalog](simplified-ui-catalog)                                     |Catalog components|
| [org.librarysimplified.ui.errorpage](simplified-ui-errorpage)                                 |Error details screen|
| [org.librarysimplified.ui.images](simplified-ui-images)                                       |Image loader API for general image resources|
| [org.librarysimplified.ui.listeners.api](simplified-ui-listeners-api)                         |Listeners API|
| [org.librarysimplified.ui.tabs](simplified-ui-navigation-tabs)                                |Tabbed UI|
| [org.librarysimplified.ui.neutrality](simplified-ui-neutrality)                               |Neutral color schemes|
| [org.librarysimplified.ui.onboarding](simplified-ui-onboarding)                               |Onboarding|
| [org.librarysimplified.ui.profiles](simplified-ui-profiles)                                   |Profiles UI|
| [org.librarysimplified.ui.screen](simplified-ui-screen)                                       |Screen API|
| [org.librarysimplified.ui.settings](simplified-ui-settings)                                   |Settings screens|
| [org.librarysimplified.ui.splash](simplified-ui-splash)                                       |Splash screen|
| [org.librarysimplified.ui.thread.api](simplified-ui-thread-api)                               |UI thread service|
| [org.librarysimplified.ui.tutorial](simplified-ui-tutorial)                                   |Tutorial screen|
| [org.librarysimplified.viewer.api](simplified-viewer-api)                                     |Viewer API|
| [org.librarysimplified.viewer.audiobook](simplified-viewer-audiobook)                         |AudioBook viewer|
| [org.librarysimplified.viewer.epub.readium2](simplified-viewer-epub-readium2)                 |Readium 2 EPUB reader|
| [org.librarysimplified.viewer.pdf-pdfjs](simplified-viewer-pdf-pdfjs)                         |PDF reader|
| [org.librarysimplified.viewer.preview](simplified-viewer-preview)                             |Book preview reader/player|
| [org.librarysimplified.viewer.spi](simplified-viewer-spi)                                     |Viewer SPI|
| [org.librarysimplified.webview](simplified-webview)                                           |WebView utilities|

_The above table is generated with [ReadMe.java](src/misc/ReadMe.java)._

### Ktlint

The codebase uses [ktlint](https://ktlint.github.io/) to enforce a consistent
code style. It's possible to ensure that any changes you've made to the code
continue to pass `ktlint` checks by running the `ktlintFormat` task to reformat
source code:  
```
$ ./gradlew ktlintFormat
```

## License

~~~
Copyright 2015 The New York Public Library, Astor, Lenox, and Tilden Foundations,
and The National Library of Finland (Kansalliskirjasto)

Licensed under the Apache License, Version 2.0 (the "License"); you may not use
this file except in compliance with the License. You may obtain a copy of the
License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed
under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
CONDITIONS OF ANY KIND, either express or implied. See the License for the
specific language governing permissions and limitations under the License.
~~~
