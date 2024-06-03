#!/bin/bash

#
# Upload an E-kirjasto build to Google Play.
#
# Version 1.0.0
#

trap 'trap - INT; exit $((128 + $(kill -l INT)))' INT

# cd into the project root directory (or fail)
cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")/.." || exit 64

# Show command usage
show_usage() {
  echo "Usage: $(basename "$0") [-h|--help] [UPLOAD_TRACK]"
  echo
  echo "This script uploads a build of the E-kirjasto app to Google Play."
  echo "The app must be built (using build.sh) before using this script"
  echo
  echo "-h   --help     Show this help page."
  echo "UPLOAD_TRACK    Track to upload to. Available tracks:"
  echo "                - internal (default): internal testing"
  echo "                - TODO: list all tracks here"
  echo
}

uploadTrack="internal"
while [[ $# -gt 0 ]]; do
  case $1 in
    -h|--help)
      show_usage
      exit 0
    ;;
    # Track to upload to
    internal|beta|production)
        uploadTrack="$1"
        shift
    ;;
    # Error on unrecognized parameters
    *)
      >&2 echo "ERROR: Unrecognized parameter: $1"
      show_usage
      exit 65
    ;;
  esac
done


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


basename "$0"

info "Uploading build to '$uploadTrack' track"

cd simplified-app-ekirjasto || exit $?

case $uploadTrack in
  internal|production)
    ../scripts/fastlane.sh "deploy_$uploadTrack" || exit $?
  ;;
  alpha|beta)
    >&2 echo "ERROR: Track upload not implemented yet: $uploadTrack"
    exit 66
  ;;
  *)
    fatal "Unrecognized upload track: $uploadTrack"
  ;;
esac
