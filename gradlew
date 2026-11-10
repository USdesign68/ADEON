#!/usr/bin/env sh
# Lightweight bootstrap wrapper for environments where the standard wrapper JAR is not bundled.
set -eu
VERSION=8.11.1
BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/manual-wrapper/gradle-$VERSION"
if [ ! -x "$BASE/bin/gradle" ]; then
  mkdir -p "$(dirname "$BASE")"
  ZIP="$(dirname "$BASE")/gradle-$VERSION-bin.zip"
  if [ ! -f "$ZIP" ]; then
    command -v curl >/dev/null 2>&1 || { echo "curl is required to download Gradle $VERSION" >&2; exit 1; }
    curl -fL "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -o "$ZIP"
  fi
  command -v unzip >/dev/null 2>&1 || { echo "unzip is required" >&2; exit 1; }
  unzip -q -o "$ZIP" -d "$(dirname "$BASE")"
fi
exec "$BASE/bin/gradle" "$@"
