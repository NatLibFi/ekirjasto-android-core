#!/bin/bash

#
# Fastlane wrapper.
#
# Version 1.0.0
#

export LC_ALL="en_US.UTF-8"
export LANG="en_US.UTF-8"

if command -v bundle > /dev/null 2>&1; then
  bundle exec fastlane "$@" || exit $?
elif command -v fastlane > /dev/null 2>&1; then
  fastlane "$@" || exit $?
else
  >&2 echo "ERROR: Could not find Fastlane or the Ruby bundler, please install Fastlane"
  exit 64
fi
