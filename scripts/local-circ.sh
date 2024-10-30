#!/bin/bash
# Updates build.gradle.kts with local IP and UUID from database

GRADLE_FILE="../simplified-app-ekirjasto/build.gradle.kts"

# Get IP and UUID for MACOS
LOCAL_IP=$(ipconfig getifaddr en0)
UUID=$(psql -U palace -h localhost -d circ -c "SELECT UUID FROM public.libraries ORDER BY id ASC" -t | xargs)

# Update first occurrences with proper indentation (macOS sed)
sed -i '' "1,/val circURL/s/val circURL.*/\t\t\tval circURL = \"$LOCAL_IP\"/" "$GRADLE_FILE"
sed -i '' "1,/val libProvider/s/val libProvider.*/\t\t\tval libProvider = \"$UUID\"/" "$GRADLE_FILE"

echo "Updated IP: $LOCAL_IP"
echo "Updated UUID: $UUID"
