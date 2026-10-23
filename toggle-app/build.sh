#!/bin/bash
# Build + sign the HA Toggle APK without Gradle.
set -e
cd "$(dirname "$0")"

export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"

SDK=/opt/homebrew/share/android-commandlinetools
BT="$SDK/build-tools/34.0.0"
ANDROID_JAR="$SDK/platforms/android-23/android.jar"

rm -rf build
mkdir -p build/classes build/dex

echo "[1/6] aapt2 link (manifest + resources)"
"$BT/aapt2" link -o build/base.apk -I "$ANDROID_JAR" \
    --manifest AndroidManifest.xml \
    --min-sdk-version 21 --target-sdk-version 23

echo "[2/6] javac"
javac -source 8 -target 8 -nowarn -bootclasspath "$ANDROID_JAR" \
    -d build/classes $(find src -name '*.java')

echo "[3/6] jar classes"
jar cf build/classes.jar -C build/classes .

echo "[4/6] d8 -> dex"
"$BT/d8" --lib "$ANDROID_JAR" --min-api 21 --output build/dex build/classes.jar

echo "[5/6] add dex + zipalign"
( cd build/dex && zip -q ../base.apk classes.dex )
"$BT/zipalign" -f 4 build/base.apk build/aligned.apk

echo "[6/6] sign"
if [ ! -f debug.keystore ]; then
  keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
      -alias hatoggle -keyalg RSA -keysize 2048 -validity 10000 \
      -dname "CN=HA Toggle" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
    --out build/hatoggle.apk build/aligned.apk
"$BT/apksigner" verify build/hatoggle.apk && echo "OK: build/hatoggle.apk"
ls -la build/hatoggle.apk
