#!/bin/bash

#
# Run E-kirjasto tests.
#
# Version 1.0.1
#

trap 'trap - INT; exit $((128 + $(kill -l INT)))' INT

# cd into the project root directory (or fail)
cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")/.." || exit 64

# Show command usage
show_usage() {
  echo "Usage: $(basename "$0") [-h|--help] [--overwrite]"
  echo
  echo "-h   --help           Show this help page."
  echo
  echo "This script runs E-kirjasto unit tests."
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

while [[ $# -gt 0 ]]; do
  case $1 in
    -h|--help)
      show_usage
      exit 0
    ;;
    # Error on unrecognized parameters
    *)
      show_usage
      fatal "Unrecognized parameter: $1" 65
    ;;
  esac
done


basename "$0"

info "Running tests"

JVM_ARGUMENTS="-Xmx4096m -XX:+PrintGC -XX:+PrintGCDetails -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8"

info "Gradle JVM arguments: ${JVM_ARGUMENTS}"

./gradlew \
  -Dorg.gradle.jvmargs="${JVM_ARGUMENTS}" \
  -Dorg.gradle.daemon=false \
  -Dorg.gradle.parallel=false \
  -Dorg.gradle.internal.publish.checksums.insecure=true \
  test \
  || fatal "Tests failed" $?
