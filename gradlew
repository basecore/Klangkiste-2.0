#!/usr/bin/env sh
set -eu
VERSION=8.10.2
if command -v gradle >/dev/null 2>&1; then exec gradle "$@"; fi
BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/klangkiste-gradle-$VERSION"
DIST="$BASE/gradle-$VERSION"
if [ ! -x "$DIST/bin/gradle" ]; then mkdir -p "$BASE"; ZIP="$BASE/gradle-$VERSION-bin.zip"; URL="https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"; if command -v curl >/dev/null 2>&1; then curl -fL --retry 3 "$URL" -o "$ZIP"; elif command -v wget >/dev/null 2>&1; then wget -O "$ZIP" "$URL"; else echo "Install Gradle 8.10.2 or curl/wget + unzip." >&2; exit 2; fi; unzip -q -o "$ZIP" -d "$BASE"; rm -f "$ZIP"; fi
exec "$DIST/bin/gradle" "$@"
