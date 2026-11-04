#!/usr/bin/env sh
# Lightweight Gradle bootstrapper. Downloads the pinned Gradle distribution on first use.
set -eu
GRADLE_VERSION="9.2.1"
GRADLE_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}/manual-wrapper/gradle-$GRADLE_VERSION"
GRADLE_BIN="$GRADLE_HOME/bin/gradle"
if [ ! -x "$GRADLE_BIN" ]; then
  BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/manual-wrapper"
  mkdir -p "$BASE"
  ZIP="$BASE/gradle-$GRADLE_VERSION-bin.zip"
  if [ ! -f "$ZIP" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -fL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ZIP"
    elif command -v wget >/dev/null 2>&1; then
      wget "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -O "$ZIP"
    else
      echo "Bitte curl oder wget installieren." >&2
      exit 1
    fi
  fi
  unzip -q -o "$ZIP" -d "$BASE"
fi
exec "$GRADLE_BIN" "$@"
