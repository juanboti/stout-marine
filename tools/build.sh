#!/bin/bash
# Builds StoutMarine.apk: ARM/BREW emulator + touch-control shell. No game files are included;
# the player picks their own BREW game (.zip with the .mod and .bar) on first launch.
# Needs: JDK 8+, Android SDK platform 23 (android.jar), dx (dalvik-exchange), aapt, zipalign, apksigner.
set -euo pipefail
cd "$(dirname "$0")/.."
SDK=${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}
B=build/apk; rm -rf $B; mkdir -p $B/classes
javac -nowarn -encoding UTF-8 -source 8 -target 8 -bootclasspath "$SDK" -d $B/classes \
  $(find src/core src/android -name '*.java') 2>&1 | grep -v -E 'Picked up|warning|^Note' || true
test -f $B/classes/modmarine/app/GameActivity.class || { echo "compile failed"; exit 1; }
dalvik-exchange --dex --min-sdk-version=21 --output=$B/classes.dex $B/classes
aapt package -f -0 arsc -M apk/AndroidManifest.xml -S apk/res -A apk/assets -I "$SDK" -F $B/unsigned.apk
(cd $B && zip -q unsigned.apk classes.dex)
zipalign -f -p 4 $B/unsigned.apk $B/aligned.apk
KS=tools/modmarine.keystore
if [ ! -f $KS ]; then
  keytool -genkeypair -keystore $KS -storepass modmarine -keypass modmarine -alias modmarine \
    -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=Stout Marine" >/dev/null 2>&1
fi
apksigner sign --ks $KS --ks-pass pass:modmarine --key-pass pass:modmarine \
  --min-sdk-version 21 --out build/StoutMarine.apk $B/aligned.apk
apksigner verify --print-certs build/StoutMarine.apk | head -1
ls -la build/StoutMarine.apk
