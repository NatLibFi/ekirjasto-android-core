#!/bin/bash

#
# Reveal E-kirjasto Android secrets.
#
# Version 1.1.1
#

trap 'trap - INT; exit $((128 + $(kill -l INT)))' INT

# cd into the project root directory (or fail)
cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")/.." || exit 64

# Show command usage
show_usage() {
  echo "Usage: $(basename "$0") [-h|--help] [--overwrite]"
  echo
  # Wrap after 80 characters --> #######################################################
  echo "Options:"
  echo "-h   --help           Show this help page."
  echo "     --overwrite      Ovewrite existing secret files."
  # Wrap after 80 characters --> #######################################################
  echo
  echo "This script reveals E-kirjasto build secrets from base64 encoded"
  echo "environment variables."
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

overwrite=0
while [[ $# -gt 0 ]]; do
  case $1 in
    -h|--help)
      show_usage
      exit 0
    ;;
    # Overwrite existing secret files
    --overwrite)
        overwrite=1
        shift
    ;;
    # Error on unrecognized parameters
    *)
      show_usage
      fatal "Unrecognized parameter: $1" 65
    ;;
  esac
done


base64_to_file() {
  base64Input="$1"
  outputFile="$2"
  echo "Revealing secret file: $outputFile"
  echo "$base64Input" | base64 --decode > "$outputFile"
}

reveal_file() {
  filepath="$1"
  envVariableName="$2"
  envVariableValue="${!envVariableName}"
  if [ "$overwrite" -ne 1 ] && [ -f "$filepath" ]; then
    info "File already exists, not overwriting: $filepath"
  else
    if [ -z "$envVariableValue" ]; then
      warn "Not revealing $filepath, because $envVariableName is not set"
    else
      base64_to_file "$envVariableValue" "$filepath"
    fi
  fi

}


basename "$0"

info "Fake secret, should be masked in logs: 'fake-secret-should-be-masked'"

reveal_file local.properties EKIRJASTO_LOCAL_PROPERTIES_BASE64
reveal_file release.jks EKIRJASTO_RELEASE_JKS_BASE64
