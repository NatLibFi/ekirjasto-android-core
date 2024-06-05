#!/bin/bash

#
# Show CI info.
#
# 1.0.0
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
