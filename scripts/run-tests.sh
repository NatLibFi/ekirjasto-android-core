#!/bin/bash

#
# Run E-kirjasto tests.
#
# Version 1.0.0
#

trap 'trap - INT; exit $((128 + $(kill -l INT)))' INT

# cd into the project root directory (or fail)
cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")/.." || exit 64

if [[ "$1" == "--help" ]] || [[ "$1" == "-h" ]]; then
  echo "TODO: Write help text"
  exit 0
fi


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
