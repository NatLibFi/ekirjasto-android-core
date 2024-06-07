fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## Android

### android get_google_play_version_code

```sh
[bundle exec] fastlane android get_google_play_version_code
```

Get the highest current version code in Google Play

### android deploy_internal

```sh
[bundle exec] fastlane android deploy_internal
```

Deploy a new release to Google Play internal testing

### android deploy_alpha

```sh
[bundle exec] fastlane android deploy_alpha
```

Deploy a new release to Google Play alpha testing

### android deploy_closed_beta

```sh
[bundle exec] fastlane android deploy_closed_beta
```

Deploy a new release to Google Play closed beta testing

### android deploy_open_beta

```sh
[bundle exec] fastlane android deploy_open_beta
```

Deploy a new release to Google Play open beta testing

### android deploy_production

```sh
[bundle exec] fastlane android deploy_production
```

Deploy a new release to Google Play production

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
