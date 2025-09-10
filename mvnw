#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper Startup Script
# ----------------------------------------------------------------------------

set -e

BASE_DIR=$(cd "$(dirname "$0")"; pwd -P)
MVNW_REPOURL=""
WRAPPER_JAR="$BASE_DIR/.mvn/wrapper/maven-wrapper.jar"
WRAPPER_PROPERTIES="$BASE_DIR/.mvn/wrapper/maven-wrapper.properties"

# Extract distributionUrl and wrapperUrl from properties
if [ ! -f "$WRAPPER_PROPERTIES" ]; then
  echo "Cannot find $WRAPPER_PROPERTIES" >&2
  exit 1
fi

# Read property
grep_prop() {
  grep "^$1=" "$WRAPPER_PROPERTIES" | cut -d'=' -f2-
}

DIST_URL=$(grep_prop distributionUrl)
WRAP_URL=$(grep_prop wrapperUrl)

# Prepare wrapper jar
if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Downloading Maven Wrapper JAR from: $WRAP_URL"
  mkdir -p "$BASE_DIR/.mvn/wrapper"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL -o "$WRAPPER_JAR" "$WRAP_URL"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O "$WRAPPER_JAR" "$WRAP_URL"
  else
    echo "Error: curl or wget is required to download Maven Wrapper JAR" >&2
    exit 1
  fi
fi

# Java command
JAVA_EXE="java"
if [ -n "$JAVA_HOME" ]; then
  if [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_EXE="$JAVA_HOME/bin/java"
  fi
fi

# Execute the wrapper
exec "$JAVA_EXE" \
  -Dmaven.multiModuleProjectDirectory="$BASE_DIR" \
  -cp "$WRAPPER_JAR" \
  org.apache.maven.wrapper.MavenWrapperMain "$@"