#!/bin/sh
set -eu
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
: "${SDK:?Set ANDROID_HOME to an Android SDK with platform/build-tools 34.}"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
[ -f "$JAR" ] && [ -x "$BT/aapt2" ] || { echo "Android SDK platform/build-tools 34 missing." >&2; exit 1; }
mkdir -p out/classes out/dex private
find out/classes out/dex -type f -delete
"$BT/aapt2" link -o out/base.apk -I "$JAR" --manifest AndroidManifest.xml --min-sdk-version 21 --target-sdk-version 30
find src -name '*.java' > out/sources.list
javac -nowarn -Xlint:-options -encoding UTF-8 -source 1.8 -target 1.8 \
    -bootclasspath "$JAR:$BT/core-lambda-stubs.jar" -d out/classes @out/sources.list
find out/classes -name '*.class' > out/classes.list
"$BT/d8" --min-api 21 --release --lib "$JAR" --output out/dex @out/classes.list
cp out/base.apk out/unsigned.apk
(cd out/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 out/unsigned.apk out/aligned.apk
KS="private/startup-helper.jks"
PASS="private/keystore-password.txt"
if [ ! -f "$KS" ]; then
    umask 077
    od -An -N24 -tx1 /dev/urandom | tr -d ' \n' > "$PASS"
    keytool -genkeypair -keystore "$KS" -storepass:file "$PASS" -keypass:file "$PASS" \
        -alias startup-helper -keyalg RSA -keysize 3072 -validity 3650 \
        -dname 'CN=Local Detox Startup Helper' >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass "file:$PASS" --out out/detox-startup-1.0.apk out/aligned.apk
"$BT/zipalign" -c 4 out/detox-startup-1.0.apk
"$BT/apksigner" verify --verbose --print-certs out/detox-startup-1.0.apk > out/signature.txt
"$BT/aapt2" dump badging out/detox-startup-1.0.apk > out/badging.txt
sha256sum out/detox-startup-1.0.apk > out/SHA256SUMS
echo 'Built out/detox-startup-1.0.apk; verification metadata is in out/.'
