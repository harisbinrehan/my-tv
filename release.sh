#!/usr/bin/env bash
# Publishes the current version as a GitHub release; installed copies of My Tv then offer it as an update.
# Usage: ./release.sh "notes shown in the app, one short line"
# Builds Apps/MyTv-v<versionCode>.apk first if it isn't there (build.sh), then uploads it with update.json
# (version, download address, SHA-256 the app checks before installing, and the notes shown in the app).
set -euo pipefail
cd "$(dirname "$0")"
NOTES="${1:?usage: ./release.sh \"notes shown in the app\"}"
REPO=$(sed -n 's/^const val RELEASES_REPO = "\(.*\)"$/\1/p' app/src/main/kotlin/com/shoqremote/app/Updater.kt)
CODE=$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts)
NAME=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)
APK="../Apps/MyTv-v$CODE.apk"
[ -n "$REPO" ] && [ -n "$CODE" ] && [ -n "$NAME" ] || { echo "couldn't read the repo or version"; exit 1; }
if gh release view "v$CODE" --repo "$REPO" >/dev/null 2>&1; then
    echo "v$CODE is already published. Bump versionCode and versionName in app/build.gradle.kts first."
    exit 1
fi
[ -f "$APK" ] || ./build.sh
SHA=$(shasum -a 256 "$APK" | cut -d' ' -f1)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
cp "$APK" "$TMP/MyTv-v$CODE.apk"
python3 -I - "$TMP/update.json" "$CODE" "$NAME" "https://github.com/$REPO/releases/download/v$CODE/MyTv-v$CODE.apk" "$SHA" "$NOTES" <<'EOF'
import json, sys
path, code, name, url, sha, notes = sys.argv[1:]
with open(path, "w") as f:
    json.dump({"versionCode": int(code), "versionName": name, "url": url, "sha256": sha, "notes": notes}, f, indent=2)
EOF
gh release create "v$CODE" "$TMP/MyTv-v$CODE.apk" "$TMP/update.json" --repo "$REPO" --title "My Tv $NAME" --notes "$NOTES"
echo "Published My Tv $NAME: https://github.com/$REPO/releases/tag/v$CODE"
