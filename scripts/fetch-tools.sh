#!/usr/bin/env bash
# Downloads the minimal Android build tools into ./tools (no Android Studio needed).
# Sources: Sable/android-platforms (android.jar), lzhiyong/android-sdk-tools (aapt2, zipalign, adb),
# pxb1988/dex2jar (dx). Linux x86_64 only.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p tools && cd tools
tmp=$(mktemp -d)
if [ ! -f android.jar ]; then
  echo "• android.jar (API 34)"
  curl -fsSL -o android.jar https://raw.githubusercontent.com/Sable/android-platforms/master/android-34/android.jar
fi
if [ ! -x aapt2 ] || [ ! -x zipalign ]; then
  echo "• aapt2 / zipalign / adb"
  curl -fsSL -o "$tmp/sdk.zip" https://github.com/lzhiyong/android-sdk-tools/releases/download/35.0.2/android-sdk-tools-static-x86_64.zip
  unzip -q -o "$tmp/sdk.zip" -d "$tmp/sdk"
  cp "$tmp/sdk/build-tools/aapt2" "$tmp/sdk/build-tools/zipalign" "$tmp/sdk/platform-tools/adb" .
  chmod +x aapt2 zipalign adb
fi
if [ ! -f dx.jar ]; then
  echo "• dx"
  curl -fsSL -o "$tmp/d2j.zip" https://github.com/pxb1988/dex2jar/releases/download/v2.4/dex-tools-v2.4.zip
  unzip -q -o -j "$tmp/d2j.zip" 'dex-tools-v2.4/lib/dx-30.0.2.jar' -d "$tmp"
  mv "$tmp/dx-30.0.2.jar" dx.jar
fi
rm -rf "$tmp"
echo "✓ الأدوات جاهزة في tools/"
