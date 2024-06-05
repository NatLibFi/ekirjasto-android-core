#!/bin/bash

#
# Run release checks for E-kirjasto.
#
# Version 1.0.0
#

trap 'trap - INT; exit $((128 + $(kill -l INT)))' INT

# cd into the project root directory (or fail)
cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")/.." || exit 64

# Show command usage
show_usage() {
  echo "Usage: $(basename "$0") [-h|--help]"
  echo
  echo "-h   --help                     Show this help page."
  echo "     --skip-transifex-download  Skip downloading new Transifex strings."
  echo
  echo "This script checks if a new E-kirjasto version is ready for release."
  echo "Release checks include:"
  echo "- the version number must be increased from the version in the main branch"
  echo "- Transifex cannot have any new strings that have not been committed to Git"
  echo
}

fatal() {
  echo "$(basename "$0"): FATAL: $1" 1>&2
  exit "${2:-1}"
}

warn() {
  echo "$(basename "$0"): WARNING: $1" 1>&2
}

info() {
  echo "$(basename "$0"): INFO: $1" 1>&2
}

skipTransifexDownload=0
while [[ $# -gt 0 ]]; do
  case $1 in
    -h|--help)
      show_usage
      exit 0
    ;;
    --skip-transifex-download)
      skipTransifexDownload=1
      shift
    ;;
    # Error on unrecognized parameters
    *)
      show_usage
      fatal "Unrecognized parameter: $1" 65
    ;;
  esac
done


#
# Get a string for comparing version.
#
# This takes a version string that follows the semantic versioning format
# (basically major.minor.patch, any suffix is ignored) and returns a comparison
# string. These comparison strings can be compared alphabetically to find which
# version is higher.
#
getVersionComparisonString() {
  version="$1"
  if grep -E '^[0-9]+\.[0-9]+\.[0-9]+' <<< "$version" > /dev/null 2>&1; then
    # The version has correct syntax (at least major.minor.patch, suffix is not checked)
    version="${version//[!0-9]/ }"
    read -ra version <<< "$version"
    major="${version[0]}"
    minor="${version[1]}"
    patch="${version[2]}"
    echo "$(printf %04d "$major")$(printf %04d "$minor")$(printf %04d "$patch")"
  else
    # Incorrect format, treat the same as 0.0.0
    echo "000000000000"
  fi
}


basename "$0"

info "Checking for version increment (comparing with the main branch)..."
# Find Git diff of the version change between the current commit and main
versionDiff="$(git diff -U0 origin/main -- gradle.properties | grep '^[+-]ekirjasto.versionName=')"
if [ -z "$versionDiff" ]; then
  fatal "The version number must be increased compared to the main branch, but no changes were found" 66
fi

# Get the old version (main branch) and the new version (current commit)
oldVersion="$(grep '^-' <<< "$versionDiff")"
oldVersion="${oldVersion#*=}"
echo "Old version: $oldVersion"
newVersion="$(grep '^+' <<< "$versionDiff")"
newVersion="${newVersion#*=}"
echo "New version: $newVersion"

oldVersionComparison="$(getVersionComparisonString "$oldVersion")"
newVersionComparison="$(getVersionComparisonString "$newVersion")"
if [[ $oldVersionComparison < $newVersionComparison ]]; then
  echo "The version in the current commit ($newVersion) is newer than the version in the main branch ($oldVersion), all good!"
else
  fatal "The version in the current commit ($newVersion) must be higher than the version in the main branch ($oldVersion)" 67
fi

if [ $skipTransifexDownload -eq 1 ]; then
  info "Skipping Transifex download because of flag..."
else
  info "Running Transifex download (skipping upload)..."
  ./scripts/transifex.sh --skip-upload || exit $?
fi

info "Checking that there are no new Transifex strings..."
changedTransifexFiles="$(git diff --name-only HEAD | grep txstrings.json)"
if [ -z "$changedTransifexFiles" ]; then
  info "Found no changes to Transifex strings, all good!"
else
  warn "Found changes to the following Transifex files:"
  echo "$changedTransifexFiles"
  message="Found unexpected changes to localizations on Transifex download, please commit any changes"
  if [ -n "$GITHUB_ACTIONS" ]; then
    # In GitHub Actions, show warnings, but don't fail the CI build
    while IFS= read -r filepath; do
      echo "::warning file=$filepath::$message"
    done <<< "$changedTransifexFiles"
  else
    # Locally, exit with an error code
    fatal "$message" 68
  fi
fi
