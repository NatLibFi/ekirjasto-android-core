# GitHub Actions workflows

This directory contains E-kirjasto Android CI workflows.


## Secrets

Secrets are stored in GitHub Actions and are available to the workflows as
environment variables.
The secrets are stored in the respective repository,
so secrets for the Android CI workflows are stored in ekirjasto-android-core,
while secrets for the iOS CI workflows are stored in ekirjasto-ios-core, etc.

GitHub Actions doesn't directly support storing files, so files are stored as
base64 encoded strings and decoded back into files (by `scripts/reveal-secrets.sh`).

[ekirjasto-ci-helper](https://github.com/NatLibFi/ekirjasto-ci-helper) is used
to manage the secrets.
It cannot read the secrets, but it can be used to set or delete secrets.
It requires a GitHub personal access token with rights to the repository.

### Android secrets

The secrets in use for the Android CI workflows are:

| Secret name                   | Environment variable                    | Format | Description                             |
|-------------------------------|-----------------------------------------|--------|-----------------------------------------|
| FASTLANE_SERVICE_ACCOUNT_JSON | EKIRJASTO_FASTLANE_SERVICE_ACCOUNT_JSON | JSON   | Google Play service account JSON key    |
| LOCAL_PROPERTIES              | EKIRJASTO_LOCAL_PROPERTIES_BASE64       | base64 | local.properties file encoded as base64 |
| RELEASE_JKS                   | EKIRJASTO_RELEASE_JKS_BASE64            | base64 | release.jks file encoded as base64      |


### Log masking secrets

In addition to the above secrets, there are some additional values where the
name starts with `MASK_`. These secrets are not used at all in the build, and
their purpose just to mask the values from GitHub Actions logs (there are other
ways to achieve log masking, but this is the easiest method).

For example, some of the values in Android's `local.properties` file should be
masked in logs, but the entire file is stored as one secret, so the individual
values would not be automatically masked without the `MASK_*` secrets.


## Android CI workflows

### android-pr.yml

This workflow is run for every commit pushed to a PR.

This only runs a debug build and tests, and doesn't upload builds anywhere.


### android-main.yml

This workflow is run for every commit pushed to the main branch.
Direct commits to main are disabled, so essentially this is run for merged PRs.

This builds debug and release builds for all flavors, runs tests,
and uploads build flavors to the following Google Play Console tracks:

| Flavor     | Uploaded to track |
|------------|------------------ |
| production | closed-beta       |
| beta       | alpha             |
| dev        | internal testing  |
| ellibs     | (not uploaded)    |


### android-release.yml

This workflow is run for commits on release/* branches (e.g. release/1.2.3).

This workflow is mostly the same as the workflow run on commits pushed to main,
meaning that all builds and tests are run, and flavors are uploaded.

In addition to that, some additional checks are run:
- the version number must be increased from the one currently in main
- the version number suffix (if any) must not contain words like "dev" or "test"
- all Transifex strings must be committed into the repository
    - i.e. `scripts/transifex.sh` must not find new strings to download

The main purpose of this workflow is to automate releasing a new version of the app,
but not everything is automated (by design). See [RELEASING.md](/RELEASING.md)
for what to do after upload and more info about E-kirjasto's releasing process.
