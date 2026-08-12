#!/usr/bin/env sh
set -eu

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
fi

MAVEN_VERSION=3.9.16
CACHE_ROOT="${MAVEN_WRAPPER_CACHE:-${HOME}/.m2/wrapper/dists}"
MAVEN_HOME="$CACHE_ROOT/apache-maven-$MAVEN_VERSION"
MAVEN_BIN="$MAVEN_HOME/bin/mvn"
URL="https://dlcdn.apache.org/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz"

if [ ! -x "$MAVEN_BIN" ]; then
  mkdir -p "$CACHE_ROOT"
  TMP_DIR=$(mktemp -d)
  trap 'rm -rf "$TMP_DIR"' EXIT INT TERM
  ARCHIVE="$TMP_DIR/maven.tar.gz"

  echo "Maven is not installed; downloading Maven $MAVEN_VERSION..." >&2
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$URL" -o "$ARCHIVE"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$URL" -O "$ARCHIVE"
  else
    echo "ERROR: Maven is not installed and neither curl nor wget is available." >&2
    exit 1
  fi

  tar -xzf "$ARCHIVE" -C "$CACHE_ROOT"
fi

cd "$BASE_DIR"
exec "$MAVEN_BIN" "$@"
