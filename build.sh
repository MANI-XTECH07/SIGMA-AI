#!/usr/bin/env bash
set -e

echo "=== Building SIGMA Android Voice Assistant ==="

# Check Java
java -version

# Build app
if [ -f "./gradlew" ]; then
    chmod +x ./gradlew
    ./gradlew testDebugUnitTest
    ./gradlew assembleDebug
else
    gradle testDebugUnitTest
    gradle assembleDebug
fi

echo "=== SIGMA Build Completed Successfully ==="
