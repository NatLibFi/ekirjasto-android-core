#!/bin/bash

#
# Show CI info.
#
# 1.3.0
#

trap 'trap - INT; exit $((128 + $(kill -l INT)))' INT

# cd into the project root directory (or fail)
cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")/.." || exit 64

basename "$0"

echo "----------------------------------------"
echo "COMMIT_SHA=$COMMIT_SHA"
echo "BRANCH_NAME=$BRANCH_NAME"
echo "TARGET_BRANCH_NAME=$TARGET_BRANCH_NAME"
echo "----------------------------------------"
echo
echo "Checking if secrets are available, fake secret: $TEST_SECRET"
if [ -z "$TEST_SECRET" ]; then
    echo "::warning title=Secrets are unavailable::Secrets are not available, this could be a non-member PR (there could be build issues)"
else
    echo "Secrets are available!"
fi
