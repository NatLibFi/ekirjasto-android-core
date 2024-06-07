# Releasing

Releasing of the E-kirjasto Android app is mostly automated through a CI workflow.

## The short version (TL;DR)

- Create a release branch, for example `release/1.2.3`
    - Fill in t
- In GitHub Actions, check that the build and all checks will pass
    - If something fails, the build will not be uploaded to Google Play Console
- Once the build is uploaded to Google Play Console
    - Promote it from the internal track to the production track
    - Send the release for app review
    - Wait for the pre-launch report to complete and check it for any issues
    - After the app passes review, it can be published


## Version numbers

The version number should follow the [Semantic Versioning](https://semver.org/) format.
Basically, the version number is of the form `major.minor.patch`.
There can also be an optional suffix after a dash (e.g. `major.minor.patch-suffix`).

The version number should be incremented as follows:
- the `major` component should increase for any major new functionality
    - the `major` component should also increase for any non-backward-compatible changes
    - if the `major` component increases, both `minor` and `patch` are "reset" to zero
        - for example, `1.2.3` becomes `2.0.0` when increasing the `major` component
- the `minor` component should increase for any new features
    - if the `minor` component increases, `patch` is "reset" to zero
        - for example, `1.2.3` becomes `1.3.0` when increasing the `minor` component
- the patch version should increase for bugfixes and other minor changes
- the version components should not have any leading zeroes
- the version components can have multiple digits (e.g. 1.0.9 can increase to 1.0.10)

## Version codes

The version code is an integer, which has to increase for every upload to Google Play Console.

The version code is automatically generated during the E-kirjasto build
This auto-generated version code is time-based (epoch timestamp since 2021-03-15 09:20:00 UTC),
but the last digit is zeroed, and then the build flavor sets the last digit to:

| Flavor     | Last digit in version code |
|------------|----------------------------|
| production | 1                          |
| beta       | 2                          |
| dev        | 3                          |
| ellibs     | 4                          |

This way, all flavors of the same build can be uploaded to Google Play Console.
But note that the flavors *must* be uploaded in the above order.
Otherwise the version code will not increase between uploads,
and Google Play Console will reject the upload.

If you want to upload the same flavor into multiple Google Play Console tracks,
you have to upload it to the "lowest" (most closed) track first,
and then promote it to the other tracks.


## Creating a new release

### Building and uploading to Google Play Console

#### Automated CI workflow (recommended)

To create a new release, create a branch of the form `release/<version>`.
For example, the release branch name could be `release/1.2.3` or `release/3.20.1-suffix`.

Edit these files for the changelog (will be visible to users in Google Play):
- `/simplified-app-ekirjasto/metadata/fi-FI/changelogs/default.txt`
- `/simplified-app-ekirjasto/metadata/en-US/changelogs/default.txt`
- `/simplified-app-ekirjasto/metadata/sv-SE/changelogs/default.txt`

When a release branch is created, the `android-release` workflow will:
- perform release checks
    - the version number must increase from the main branch (any suffix is ignored here)
    - there must not be any uncommitted Transifex strings
        - these should be downloaded using `./transifex.sh`
            - fill in the Transifex secret and token in `local.properties`
- build both debug and release builds for all flavors
- upload builds to different tracks in Google Play Console

If the release checks and everything else in the CI workflow goes okay,
the build flavors will be uploaded to the following tracks:

| Flavor     | Uploaded to track |
|------------|------------------ |
| production | closed-beta       |
| beta       | alpha             |
| dev        | internal testing  |
| ellibs     | (not uploaded)    |


#### Manual build and upload

Alternatively, the release process can be done manually.

In order to perform a release build, you need:
- the upload keystore and it's passwords
    - it should be saved at the project's root directory as `release.jks`
    - the filename is because of "legacy" reasons, but it's used as an AAB upload keystore
- secret values in `local.properties`
    - make a copy of `local.properties.exmaple` and fill in the correct values

Then, build the release version of the production flavor:
- either by running `./scripts/build.sh release`
- or in Android Studio
    - select the `simplified-app-ekirjasto` target (near the top-right)
    - go to: `View` > `Tool Windows` > `Build Variants`
    - select the `productionRelease` variant for `simplified-app-ekirjasto`
    - go to: `Build` > `Build App Bundle(s) / APK(s)` > `Build Bundle(s)`
    - select the release keystore file and input the passwords
        - you can choose to remember these for the next time

The built AAB file will be located at
- `build/outputs/bundle/productionRelease/ekirjasto-production-release.aab`

Once the AAB is successfully built and signed, it can be uploaded to Google Play Console:
- either by running `./scripts/fastlane.sh deploy_internal`
    - set the `EKIRJASTO_FASTLANE_SERVICE_ACCOUNT_JSON` environment variable
        - this should contain the Google Play Console service account JSON file contents (not the filepath)
- or by manually uploading the AAB into the internal track in Google Play Console

### Publishing an uploaded build

After a new build is uploaded to Google Play Console:
- double-check that you won't accidentally promote a non-production build to production
- the pre-launch report should be checked for any (major) issues
    - there will likely be accessibility issues, but some are from embedded websites
- the app should be sent for review
- final internal testing should be done with an APK downloaded from Google Play Console

The pre-launch report takes *at least* an hour to complete, so you should
immediately promote an intended production release build from the internal track
to the production track and send it for app review.

App review takes it's own time (sometimes just 15 minutes, sometimes days),
and the build will *not* be automatically released to production after review
(since "Managed publishing" *should* be turned on in Google Play Console).
So, it's safe to send a build for review, this doesn't mean that it must be released.

Assuming review passes, the app can be published to production!
