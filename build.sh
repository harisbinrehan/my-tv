#!/usr/bin/env bash
# Builds the release APK and files it as ../Apps/MyTv-v<versionCode>.apk (the My Tv folder's Apps folder).
# Bump versionCode in app/build.gradle.kts first: an existing version is never overwritten.
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
GRADLE="$HOME/.local/gradle-8.11.1/bin/gradle"
[ -x "$GRADLE" ] || GRADLE=./gradlew

VERSION=$(sed -n 's/^ *versionCode = \([0-9]*\)$/\1/p' app/build.gradle.kts)
APPS="$(cd .. && pwd)/Apps"
OUT="$APPS/MyTv-v$VERSION.apk"
if [ -e "$OUT" ]; then
    echo "$OUT already exists. Bump versionCode in app/build.gradle.kts first." >&2
    exit 1
fi

"$GRADLE" :atvremote:test :app:assembleRelease --console=plain
mkdir -p "$APPS"
cp app/build/outputs/apk/release/app-release.apk "$OUT"
echo "APK: $OUT"
