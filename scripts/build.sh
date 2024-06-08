#!/bin/bash

#
# Build the E-kirjasto Android app.
#
# Version 1.1.1
#

trap 'trap - INT; exit $((128 + $(kill -l INT)))' INT

# cd into the project root directory (or fail)
cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")/.." || exit 64

# Show command usage
show_usage() {
  echo "Usage: $(basename "$0") [-h|--help] [BUILD_TYPE]"
  echo
  echo "-h   --help     Show this help page."
  echo "BUILD_TYPE      Build type to use. Available build types:"
  echo "                - debug (default): debug build"
  echo "                - release: release build (requires signing keys)"
  echo "                - all: both debug and release builds"
  echo
  echo "This script builds the E-kirjasto app (all flavors)."
  echo "This is mostly used for CI builds, but can be used locally as well."
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

buildType="debug"
while [[ $# -gt 0 ]]; do
  case $1 in
    -h|--help)
      show_usage
      exit 0
    ;;
    # Build type
    debug|release|all)
        buildType="$1"
        shift
    ;;
    # Error on unrecognized parameters
    *)
      show_usage
      fatal "Unrecognized parameter: $1" 65
    ;;
  esac
done


basename "$0"

info "Executing '$buildType' build"

jvmArguments="-Xmx4096m -XX:+PrintGC -XX:+PrintGCDetails -XX:MaxMetaspaceSize=1024m -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8"

info "Gradle JVM arguments: ${jvmArguments}"

case $buildType in
  debug)
    ./gradlew \
      -Dorg.gradle.jvmargs="${jvmArguments}" \
      -Dorg.gradle.daemon=false \
      -Dorg.gradle.parallel=false \
      -Dorg.gradle.internal.publish.checksums.insecure=true \
      assembleDebug verifySemanticVersioning \
      || fatal "Debug build failed" $?
  ;;
  release)
    ./gradlew \
      -Dorg.gradle.jvmargs="${jvmArguments}" \
      -Dorg.gradle.daemon=false \
      -Dorg.gradle.parallel=false \
      -Dorg.gradle.internal.publish.checksums.insecure=true \
      assemble verifySemanticVersioning \
      || fatal "Release build failed" $?
  ;;
  all)
    ./gradlew \
      -Dorg.gradle.jvmargs="${jvmArguments}" \
      -Dorg.gradle.daemon=false \
      -Dorg.gradle.parallel=false \
      -Dorg.gradle.internal.publish.checksums.insecure=true \
      assembleDebug assemble verifySemanticVersioning \
      || fatal "Debug and release build failed" $?
  ;;
  *)
    fatal "Unrecognized build type: $buildType"
  ;;
esac
