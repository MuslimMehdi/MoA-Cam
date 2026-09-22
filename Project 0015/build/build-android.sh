#!/usr/bin/env bash
set -euo pipefail
cd android
if [ -x ./gradlew ]; then ./gradlew assembleDebug; else gradle assembleDebug; fi
echo "APK: app/build/outputs/apk/debug/app-debug.apk"
