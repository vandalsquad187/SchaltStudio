#!/bin/bash
# SchaltStudio — APK-Build ohne Gradle (aapt2 + javac + d8 + apksigner)
set -e

SDK=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/home/x/android-sdk}}
BT=$SDK/build-tools/36.0.0
PLAT=$SDK/platforms/android-36/android.jar
JAHOME=${JAVA_HOME:-/opt/android-studio/jbr}
PROJ="$(cd "$(dirname "$0")" && pwd)"

rm -rf "$PROJ/build"
mkdir -p "$PROJ/build/classes" "$PROJ/build/gen" "$PROJ/dist"

echo "== aapt2 compile =="
"$BT/aapt2" compile --dir "$PROJ/res" -o "$PROJ/build/res.zip"

echo "== aapt2 link =="
"$BT/aapt2" link -o "$PROJ/build/base.apk" \
    -I "$PLAT" \
    --manifest "$PROJ/AndroidManifest.xml" \
    --java "$PROJ/build/gen" \
    --min-sdk-version 26 --target-sdk-version 35 \
    --version-code 4 --version-name 1.3 \
    --auto-add-overlay \
    "$PROJ/build/res.zip"

echo "== javac =="
"$JAHOME/bin/javac" -source 8 -target 8 -encoding UTF-8 -Xlint:-options -nowarn \
    -classpath "$PLAT" \
    -d "$PROJ/build/classes" \
    $(find "$PROJ/java" "$PROJ/build/gen" -name "*.java")

echo "== d8 =="
PATH="$JAHOME/bin:$PATH" "$BT/d8" --release --lib "$PLAT" --min-api 26 \
    --output "$PROJ/build" \
    $(find "$PROJ/build/classes" -name "*.class")

echo "== paketieren =="
cd "$PROJ/build"
zip -q base.apk classes.dex
"$BT/zipalign" -p -f 4 base.apk aligned.apk

echo "== signieren =="
if [ ! -f "$PROJ/keystore.jks" ]; then
  "$JAHOME/bin/keytool" -genkeypair -keystore "$PROJ/keystore.jks" \
    -alias schalt -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass schaltstudio -keypass schaltstudio \
    -dname "CN=SchaltStudio"
fi
PATH="$JAHOME/bin:$PATH" "$BT/apksigner" sign --ks "$PROJ/keystore.jks" \
    --ks-pass pass:schaltstudio --key-pass pass:schaltstudio \
    --ks-key-alias schalt \
    --out "$PROJ/dist/SchaltStudio.apk" aligned.apk

echo "OK: $PROJ/dist/SchaltStudio.apk"
