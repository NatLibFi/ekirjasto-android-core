#!/bin/bash
# Updates build.gradle.kts with local IP and UUID from database

# Script configuration
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)"
GRADLE_FILE="$PROJECT_ROOT/simplified-app-ekirjasto/build.gradle.kts"

# Check required commands
REQUIRED_COMMANDS=("ipconfig" "psql" "git" "sed")
for cmd in "${REQUIRED_COMMANDS[@]}"; do
    if ! command -v "$cmd" &>/dev/null; then
        echo "Error: Required command '$cmd' not found"
        exit 1
    fi
done

# Get IP and UUID for MACOS
LOCAL_IP=$(ipconfig getifaddr en0)
UUID=$(psql -U palace -h localhost -d circ -c "SELECT UUID FROM public.libraries ORDER BY id ASC" -t | xargs)

# Validate values
[ -z "$LOCAL_IP" ] || [ -z "$UUID" ] && { echo "Error: Failed to get IP or UUID"; exit 1; }

# Replace the values using sed
sed -i '' "s|val circURL = \"LOCAL_IP_ADDRESS\"|val circURL = \"$LOCAL_IP\"|g" "$GRADLE_FILE"
sed -i '' "s|val libProvider = \"LOCAL_CIRC_LIBRARY_UUID\"|val libProvider = \"$UUID\"|g" "$GRADLE_FILE"

echo "Updated IP: $LOCAL_IP"
echo "Updated UUID: $UUID"
