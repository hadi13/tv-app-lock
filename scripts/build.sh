#!/usr/bin/env bash
# Builds and signs dist/TVLock.apk.  Needs: bash, curl, unzip, Java 17+ (javac, jarsigner, keytool),
# Python 3 with the "cryptography" package (pip install cryptography).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
T="$ROOT/tools"; A="$ROOT/app"; B="$ROOT/build"
[ -f "$T/android.jar" ] && [ -x "$T/aapt2" ] && [ -f "$T/dx.jar" ] || "$ROOT/scripts/fetch-tools.sh"

KEY="${TVLOCK_KEYSTORE:-$ROOT/keystore/tvlock.p12}"
PASS="${TVLOCK_KEYPASS:-tvlock123}"
if [ ! -f "$KEY" ]; then
  echo "• إنشاء مفتاح توقيع جديد: $KEY"
  mkdir -p "$(dirname "$KEY")"
  keytool -genkeypair -storetype PKCS12 -keystore "$KEY" -storepass "$PASS" -keypass "$PASS" \
    -alias tvlock -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=TV App Lock" >/dev/null 2>&1
fi

rm -rf "$B"; mkdir -p "$B/classes" "$B/dex" "$ROOT/dist"
echo "• الموارد"
"$T/aapt2" compile --dir "$A/res" -o "$B/res.zip"
"$T/aapt2" link -I "$T/android.jar" --manifest "$A/AndroidManifest.xml" -o "$B/unsigned.apk" \
  --min-sdk-version 21 --target-sdk-version 29 --java "$B/gen" -A "$A/assets" "$B/res.zip"
echo "• Java"
javac -encoding UTF-8 --release 8 -nowarn -Xlint:none -cp "$T/android.jar" -d "$B/classes" \
  $(find "$A/src" "$B/gen" -name '*.java') 2>&1 | grep -v -E '^(warning|Note|[0-9]+ warnings?)' || true
[ -f "$B/classes/com/hadi/tvlock/LockService.class" ] || { echo "✗ فشل تجميع الكود"; exit 1; }
echo "• dex"
java -jar "$T/dx.jar" --dex --min-sdk-version=21 --output="$B/dex/classes.dex" "$B/classes"
(cd "$B/dex" && zip -q -j "$B/unsigned.apk" classes.dex)
"$T/zipalign" -f -p 4 "$B/unsigned.apk" "$B/aligned.apk"
echo "• التوقيع (v1 + v2)"
cp "$B/aligned.apk" "$B/v1.apk"
jarsigner -keystore "$KEY" -storetype PKCS12 -storepass "$PASS" -sigalg SHA256withRSA -digestalg SHA-256 \
  "$B/v1.apk" "$(keytool -list -storetype PKCS12 -keystore "$KEY" -storepass "$PASS" 2>/dev/null | awk -F, '/PrivateKeyEntry/{print $1; exit}')" >/dev/null
"$T/zipalign" -f -p 4 "$B/v1.apk" "$B/v1a.apk"
python3 "$ROOT/scripts/v2sign.py" "$B/v1a.apk" "$ROOT/dist/TVLock.apk" "$KEY" "$PASS"
echo "✓ dist/TVLock.apk ($(du -h "$ROOT/dist/TVLock.apk" | cut -f1))"
