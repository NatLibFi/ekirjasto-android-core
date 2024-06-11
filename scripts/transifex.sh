#!/bin/bash

#
# Transifex Android wrapper.
#
# Version 2.2.0
#

basename "$0"

trap 'trap - INT; exit $((128 + $(kill -l INT)))' INT

# cd into the project root directory (or fail)
cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")/.." || exit 64

# Show command usage
show_usage() {
  echo "Usage: $(basename "$0") [-h|--help]"
  echo
  # Wrap after 80 characters --> #######################################################
  echo "Options:"
  echo "-h   --help           Show this help page."
  echo "     --append-tags    Append the listed tags to all strings (comma-separated)."
  echo "     --dry-run        Perform a dry-run of an upload."
  echo "     --purge          Purge any deleted source strings."
  echo "     --skip-upload    Skip upload even if the Transifex secret is given."
  # Wrap after 80 characters --> #######################################################
  echo
  echo "This script uploads and downloads Transifex strings."
  echo
  echo "The Transifex token (required) and secret (optional) are read automatically"
  echo "from local.properties, if they're set there, or they can be set by using the"
  echo "environment variables TRANSIFEX_TOKEN and TRANSIFEX_SECRET."
  echo
  echo "The script always downloads/pulls strings from Transifex, so the token"
  echo "is required. The Transifex secret is optional, and if not given, then"
  echo "upload/push will be skipped."
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

appendTags=""
dryRun=0
purge=0
skipUpload=0
while [[ $# -gt 0 ]]; do
  case $1 in
    -h|--help)
      show_usage
      exit 0
    ;;
    --append-tags=*)
      appendTags="${1#*=}"
      shift
    ;;
    --dry-run)
      dryRun=1
      shift
    ;;
    --purge)
      purge=1
      shift
    ;;
    --skip-upload)
      skipUpload=1
      shift
    ;;
    # Error on unrecognized parameters
    *)
      show_usage
      fatal "Unrecognized parameter: $1" 65
    ;;
  esac
done

if [[ "$appendTags" == *" "* ]]; then
  fatal "No spaces allowed in --appendTags"
fi

if [ $skipUpload -eq 1 ] && [ -n "$appendTags" ]; then
  fatal "Cannot use --skip-upload and --append-tags together (cannot upload tags without uploading strings)" 66
fi

if [ $skipUpload -eq 1 ] && [ $dryRun -eq 1 ]; then
  fatal "Cannot use --skip-upload and --dry-run together (dry-runs are only for uploads, not downloads)" 67
fi

if [ $skipUpload -eq 1 ] && [ $purge -eq 1 ]; then
  fatal "Cannot use --skip-upload and --purge together (purge is only effective when uploading)" 68
fi

# Path to app assets directory
assetsPath="simplified-app-ekirjasto/src/main/assets"

if [ -z "${TRANSIFEX_TOKEN}" ]; then
  info "Looking for Transifex token in local.properties"
  TRANSIFEX_TOKEN="$(grep "transifex.token=" local.properties 2> /dev/null)"
  TRANSIFEX_TOKEN="${TRANSIFEX_TOKEN#transifex.token=}"
  if [ -z "${TRANSIFEX_TOKEN}" ]; then
    fatal "TRANSIFEX_TOKEN is not defined and could not find it in local.properties" 69
  fi
fi

#------------------------------------------------------------------------
# Download and verify Transifex.
#

info "Downloading transifex.jar"

curl -sLo transifex.jar https://github.com/transifex/transifex-java/releases/download/1.3.0/transifex.jar \
  || fatal "Could not download Transifex" 70

sha256sum -c transifex.sha256 \
  || fatal "Could not verify transifex.jar" 71

#------------------------------------------------------------------------
# Apply Transifex to the project's string resources.
#

STRING_FILES=$(find . -name '*strings*.xml' -type f | sort) \
  || fatal "Could not list string files" 72

info "Files to upload strings from:"
echo "$STRING_FILES"
echo

if [ $skipUpload -eq 0 ] && [ -z "${TRANSIFEX_SECRET}" ]; then
  info "Looking for Transifex secret in local.properties"
  TRANSIFEX_SECRET="$(grep "transifex.secret=" local.properties 2> /dev/null)"
  TRANSIFEX_SECRET="${TRANSIFEX_SECRET#transifex.secret=}"
fi

if [ $skipUpload -eq 1 ]; then
  info "Skipping Transifex upload because of flag..."
elif [ -z "${TRANSIFEX_SECRET}" ]; then
  echo
  warn "TRANSIFEX_SECRET is not defined and could not find it in local.properties, UPLOAD WILL BE SKIPPED"
  echo
else
  TRANSIFEX_PUSH_ARGS="--verbose"

  if [ $dryRun -eq 1 ]; then
    TRANSIFEX_PUSH_ARGS="${TRANSIFEX_PUSH_ARGS} --dry-run"
  fi

  if [ $purge -eq 1 ]; then
    TRANSIFEX_PUSH_ARGS="${TRANSIFEX_PUSH_ARGS} --purge"
  fi

  if [ -n "$appendTags" ]; then
    TRANSIFEX_PUSH_ARGS="${TRANSIFEX_PUSH_ARGS} --append-tags=$appendTags"
  fi

  TRANSIFEX_PUSH_ARGS="${TRANSIFEX_PUSH_ARGS} --token=${TRANSIFEX_TOKEN}"
  TRANSIFEX_PUSH_ARGS="${TRANSIFEX_PUSH_ARGS} --secret=${TRANSIFEX_SECRET}"

  for FILE in ${STRING_FILES}; do
    TRANSIFEX_PUSH_ARGS="${TRANSIFEX_PUSH_ARGS} --file=${FILE}"
  done

  info "Uploading Transifex strings"

  java -jar transifex.jar push ${TRANSIFEX_PUSH_ARGS} \
    || fatal "Could not upload Transifex strings" 73

  info "Upload done!"
  echo
fi

TRANSIFEX_PULL_ARGS="--token=${TRANSIFEX_TOKEN}"
TRANSIFEX_PULL_ARGS="${TRANSIFEX_PULL_ARGS} --dir=$assetsPath"

# Get list of languages from gradle.properties
languages="$(grep "ekirjasto.languages=" gradle.properties)"
languages="${languages#ekirjasto.languages=}"
IFS="," read -r -a languages <<< "$languages"
info "Languages: ${languages[*]}"
for language in "${languages[@]}"; do
  TRANSIFEX_PULL_ARGS="${TRANSIFEX_PULL_ARGS} --locales=$language"
done

info "Downloading Transifex strings"

java -jar transifex.jar pull ${TRANSIFEX_PULL_ARGS} \
  || fatal "Could not download Transifex strings" 74

info "Download done!"
echo

#------------------------------------------------------------------------
# Prettify Transifex JSON files.
#

for transifexFile in "$assetsPath/txnative/"*/txstrings.json; do
  info "Prettifying JSON file: $transifexFile"
  # jq might be nicer than json_pp, but it's usually available on macOS and Linux
  { json_pp > "$transifexFile~"; } < "$transifexFile"
  mv "$transifexFile~" "$transifexFile"
done
