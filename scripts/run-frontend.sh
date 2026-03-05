#!/bin/bash

# Script to launch ONLY the frontend
# Run this from an external terminal (not IntelliJ)

# Get the project root directory (parent of scripts/)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_DIR="$(dirname "$SCRIPT_DIR")"
cd "$BASE_DIR"

echo "=========================================="
echo "  Starting JTypoSquatting Frontend"
echo "=========================================="
echo ""

# Kill any existing frontend
echo "Stopping existing frontend..."
pkill -9 -f "JTypoFrame" 2>/dev/null
sleep 1

JAVA=/app/extra/jbr/bin/java

# Build if needed
if [ ! -f "frontend/build/classes/java/main/com/aleph/graymatter/jtyposquatting/ui/JTypoFrame.class" ]; then
    echo "Building frontend..."
    ./gradlew :frontend:build --no-daemon -q
fi

echo "Starting frontend..."
echo ""

$JAVA -Djava.awt.headless=false \
    -cp "frontend/build/classes/java/main:frontend/build/resources/main:shared/build/libs/shared-1.0-SNAPSHOT.jar:\
$HOME/.gradle/caches/modules-2/files-2.1/com.google.guava/guava/33.1.0-jre/9b7ed39143d59e8eabcc6f91ffe4d23db2efe558/guava-33.1.0-jre.jar:\
$HOME/.gradle/caches/modules-2/files-2.1/com.googlecode.json-simple/json-simple/1.1.1/c9ad4a0850ab676c5c64461a05ca524cdfff59f1/json-simple-1.1.1.jar:\
$HOME/.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.10.1/b3add478d4382b78ea20b1671390a858002feb6c/gson-2.10.1.jar" \
    com.aleph.graymatter.jtyposquatting.ui.JTypoFrame
