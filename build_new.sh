#!/usr/bin/env bash

set -Eeuo pipefail

PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

GRADLE_FILE="$PROJECT_DIR/app/build.gradle.kts"
UNSIGNED_APK="$PROJECT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"

PROJECT_VERSION="$(sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$GRADLE_FILE" | head -n 1)"
CURRENT_VERSION_CODE="$(sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+).*/\1/p' "$GRADLE_FILE" | head -n 1)"
if [[ -z "$PROJECT_VERSION" ]]; then
    echo "Fehler: versionName nicht in $GRADLE_FILE gefunden." >&2
    exit 1
fi
if [[ -z "$CURRENT_VERSION_CODE" ]]; then
    echo "Fehler: versionCode nicht in $GRADLE_FILE gefunden." >&2
    exit 1
fi

SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK_DIR" && -f "$PROJECT_DIR/local.properties" ]]; then
    SDK_DIR="$(sed -nE 's/^sdk\.dir=(.*)$/\1/p' "$PROJECT_DIR/local.properties" | head -n 1)"
fi
if [[ -z "$SDK_DIR" || ! -d "$SDK_DIR/build-tools" ]]; then
    echo "Fehler: Android SDK nicht gefunden. ANDROID_HOME setzen oder sdk.dir in local.properties eintragen." >&2
    exit 1
fi

BUILD_TOOLS_DIR="$(find "$SDK_DIR/build-tools" -mindepth 1 -maxdepth 1 -type d -print | sort -V | tail -n 1)"
APKSIGNER="$BUILD_TOOLS_DIR/apksigner"
ZIPALIGN="$BUILD_TOOLS_DIR/zipalign"
AAPT2="$BUILD_TOOLS_DIR/aapt2"
if [[ ! -x "$APKSIGNER" || ! -x "$ZIPALIGN" || ! -x "$AAPT2" ]]; then
    echo "Fehler: apksigner, zipalign oder aapt2 fehlt in $BUILD_TOOLS_DIR." >&2
    exit 1
fi

# Höchste tatsächlich im Manifest gespeicherte Version vorhandener Release-APKs.
shopt -s nullglob
EXISTING_APKS=("$PROJECT_DIR"/tenet-*-release.apk)
shopt -u nullglob
LATEST_APK_VERSION=""
if (( ${#EXISTING_APKS[@]} > 0 )); then
    LATEST_APK_VERSION="$({
        for apk in "${EXISTING_APKS[@]}"; do
            "$AAPT2" dump badging "$apk" 2>/dev/null |
                sed -nE "s/^package:.*versionName='([^']+)'.*/\1/p" |
                head -n 1
        done
    } | sort -V | tail -n 1)"
fi

if [[ -n "$LATEST_APK_VERSION" ]]; then
    echo "Aktuellste gebaute APK: $LATEST_APK_VERSION"
else
    echo "Aktuellste gebaute APK: keine gefunden"
fi
echo "Aktuelle Projektversion: $PROJECT_VERSION (versionCode $CURRENT_VERSION_CODE)"

VERSION_BASE="$PROJECT_VERSION"
if [[ -n "$LATEST_APK_VERSION" ]]; then
    VERSION_BASE="$(printf '%s\n%s\n' "$PROJECT_VERSION" "$LATEST_APK_VERSION" | sort -V | tail -n 1)"
fi
IFS=. read -r SUGGESTED_MAJOR SUGGESTED_MINOR SUGGESTED_PATCH <<< "$VERSION_BASE"
if [[ "$SUGGESTED_MAJOR" =~ ^[0-9]+$ && "$SUGGESTED_MINOR" =~ ^[0-9]+$ && "$SUGGESTED_PATCH" =~ ^[0-9]+$ ]]; then
    SUGGESTED_VERSION="$SUGGESTED_MAJOR.$SUGGESTED_MINOR.$((SUGGESTED_PATCH + 1))"
else
    SUGGESTED_VERSION="$PROJECT_VERSION"
fi

read -r -p "Welche Version bauen? [$SUGGESTED_VERSION]: " VERSION_NAME
VERSION_NAME="${VERSION_NAME:-$SUGGESTED_VERSION}"
VERSION_NAME="${VERSION_NAME#v}"
if [[ ! "$VERSION_NAME" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z]+)*$ ]]; then
    echo "Fehler: Ungültige Version '$VERSION_NAME'. Beispiel: 0.26.0 oder 0.26.0-beta1" >&2
    exit 1
fi

NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))
GRADLE_TEMP="$(mktemp "${TMPDIR:-/tmp}/tenet-gradle.XXXXXX")"
sed -E \
    -e "s/^([[:space:]]*versionCode[[:space:]]*=[[:space:]]*)[0-9]+/\\1$NEW_VERSION_CODE/" \
    -e "s/^([[:space:]]*versionName[[:space:]]*=[[:space:]]*)\"[^\"]+\"/\\1\"$VERSION_NAME\"/" \
    "$GRADLE_FILE" > "$GRADLE_TEMP"
mv -- "$GRADLE_TEMP" "$GRADLE_FILE"

echo "Neue App-Version: $VERSION_NAME (versionCode $NEW_VERSION_CODE)"

# Standardmäßig derselbe lokale Schlüssel wie bei bisherigen installierbaren Builds.
# Eigener Schlüssel: KEYSTORE=/pfad/key.jks KEY_ALIAS=name KS_PASS=... KEY_PASS=... ./build_new.sh
KEYSTORE="${KEYSTORE:-$HOME/.android/debug.keystore}"
KEY_ALIAS="${KEY_ALIAS:-androiddebugkey}"
KS_PASS="${KS_PASS:-android}"
KEY_PASS="${KEY_PASS:-android}"
OUTPUT_APK="$PROJECT_DIR/tenet-${VERSION_NAME}-release.apk"

if [[ ! -f "$KEYSTORE" ]]; then
    echo "Fehler: Keystore fehlt: $KEYSTORE" >&2
    exit 1
fi

TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/tenet-build.XXXXXX")"
trap 'rm -rf -- "$TEMP_DIR"' EXIT
ALIGNED_APK="$TEMP_DIR/tenet-aligned.apk"

echo "Baue Tenet $VERSION_NAME …"
./gradlew :app:assembleRelease --console=plain

if [[ ! -f "$UNSIGNED_APK" ]]; then
    echo "Fehler: Build erfolgreich, aber APK fehlt: $UNSIGNED_APK" >&2
    exit 1
fi

echo "Richte APK aus und signiere …"
"$ZIPALIGN" -f -P 16 4 "$UNSIGNED_APK" "$ALIGNED_APK"
"$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias "$KEY_ALIAS" \
    --ks-pass "pass:$KS_PASS" \
    --key-pass "pass:$KEY_PASS" \
    --out "$OUTPUT_APK" \
    "$ALIGNED_APK"

"$APKSIGNER" verify --verbose "$OUTPUT_APK"
"$ZIPALIGN" -c -P 16 4 "$OUTPUT_APK"

BUILT_VERSION="$(
    "$AAPT2" dump badging "$OUTPUT_APK" |
        sed -nE "s/^package:.*versionName='([^']+)'.*/\1/p" |
        head -n 1
)"
if [[ "$BUILT_VERSION" != "$VERSION_NAME" ]]; then
    echo "Fehler: APK meldet Version '$BUILT_VERSION' statt '$VERSION_NAME'." >&2
    exit 1
fi

SIZE="$(du -h "$OUTPUT_APK" | awk '{print $1}')"
echo
echo "Fertig: $OUTPUT_APK ($SIZE, Version $BUILT_VERSION)"
