#!/bin/bash

#
# Fastlane wrapper.
#
# Version 2.0.0
#

trap 'trap - INT; exit $((128 + $(kill -l INT)))' INT

# cd into the Fastlane project directory, or fail
cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")/../simplified-app-ekirjasto" || exit 64

fatal() {
  echo "$(basename "$0"): FATAL: $1" 1>&2
  exit "${2:-1}"
}

# Set some environment variables to make Fastlane work smoothly
export LC_ALL="en_US.UTF-8"
export LANG="en_US.UTF-8"

if command -v bundle > /dev/null 2>&1; then
  bundle install || exit $?
  bundle exec fastlane "$@" || exit $?
elif command -v fastlane > /dev/null 2>&1; then
  fastlane "$@" || exit $?
else
  fatal "Could not find Fastlane or the Ruby bundler, please install Fastlane" 65
fi
