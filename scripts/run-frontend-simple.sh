#!/bin/bash

# Simple script to launch ONLY the frontend
# Run from external terminal after backend is started

cd "$(dirname "$0")"

# Build if needed
if [ ! -f "frontend/build/classes/java/main/com/aleph/graymatter/jtyposquatting/ui/JTypoFrame.class" ]; then
    echo "Building frontend..."
    ./gradlew :frontend:build --no-daemon -q
fi

echo "Starting frontend..."

# Use Gradle to run (handles classpath automatically)
exec ./gradlew :frontend:run --no-daemon
