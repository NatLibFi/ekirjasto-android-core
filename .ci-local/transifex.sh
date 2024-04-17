#!/bin/bash

#
# Transifex wrapper
#
# Version 1.0.0
#

basename "$0"

trap 'trap - INT; exit $((128 + $(kill -l INT)))' INT

# cd into the project root directory (or fail)
cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")/.." || exit 64

#------------------------------------------------------------------------
# Utility methods
#

fatal() {
  echo "$(basename "$0"): FATAL: $1" 1>&2
  exit "${2:-1}"
}

info() {
  echo "$(basename "$0"): INFO: $1" 1>&2
}

assetsPath="simplified-app-ekirjasto/src/main/assets"

if [ -z "${TRANSIFEX_TOKEN}" ]; then
  info "TRANSIFEX_TOKEN is not defined, trying to look for the token in secrets.conf"
  secretsConfPath="$assetsPath/secrets.conf"
  TRANSIFEX_TOKEN="$(grep "transifex.token=" "$secretsConfPath" 2> /dev/null)"
  TRANSIFEX_TOKEN="${TRANSIFEX_TOKEN#transifex.token=}"
  if [ -z "${TRANSIFEX_TOKEN}" ]; then
    fatal "TRANSIFEX_TOKEN is not defined and could not find token in secrets.conf" 65
  fi
fi

#------------------------------------------------------------------------
# Download and verify Transifex.
#

info "Downloading transifex.jar"

wget -c https://github.com/transifex/transifex-java/releases/download/1.3.0/transifex.jar \
  || fatal "Could not download Transifex" 66

sha256sum -c .ci-local/transifex.sha256 \
  || fatal "Could not verify transifex.jar" 67

#------------------------------------------------------------------------
# Apply Transifex to the project's string resources.
#

if [ -z "${TRANSIFEX_SECRET}" ]; then
  info "TRANSIFEX_SECRET is not defined, will skip Transifex upload"
else
  STRING_FILES=$(find . -name 'strings*.xml' -type f) \
    || fatal "Could not list string files" 68

  TRANSIFEX_PUSH_ARGS="--verbose"
  TRANSIFEX_PUSH_ARGS="${TRANSIFEX_PUSH_ARGS} --token=${TRANSIFEX_TOKEN}"
  TRANSIFEX_PUSH_ARGS="${TRANSIFEX_PUSH_ARGS} --secret=${TRANSIFEX_SECRET}"

  for FILE in ${STRING_FILES}; do
    TRANSIFEX_PUSH_ARGS="${TRANSIFEX_PUSH_ARGS} --file=${FILE}"
  done

  info "Uploading Transifex strings"

  java -jar transifex.jar push ${TRANSIFEX_PUSH_ARGS} \
    || fatal "Could not upload Transifex strings" 69
fi

TRANSIFEX_PULL_ARGS="--token=${TRANSIFEX_TOKEN}"
TRANSIFEX_PULL_ARGS="${TRANSIFEX_PULL_ARGS} --dir=simplified-app-ekirjasto/src/main/assets"

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
  || fatal "Could not download Transifex strings" 70

#------------------------------------------------------------------------
# Prettify Transifex JSON files.
#

for transifexFile in "$assetsPath/txnative/"*/txstrings.json; do
  info "Prettifying JSON file: $transifexFile"
  # jq might be nicer than json_pp, but it's usually available on macOS and Linux
  { json_pp > "$transifexFile~"; } < "$transifexFile"
  mv "$transifexFile~" "$transifexFile"
done
