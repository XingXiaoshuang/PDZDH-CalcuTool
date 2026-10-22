#!/usr/bin/env bash
# Build the release-ready APK without Gradle (aapt2 + javac + d8 + apksigner).
#
# Requirements: JDK 17+ and an Android SDK with build-tools 34.0.0 and platform android-34.
# Override the SDK location with ANDROID_HOME, e.g.  ANDROID_HOME=D:/sdk ./build.sh
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-F:/Android-SDK}}"
BT="$SDK/build-tools/34.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
if [ -n "${JAVA_HOME:-}" ]; then
  JH="$(cygpath -w "$JAVA_HOME" 2>/dev/null || echo "$JAVA_HOME")"
  export JAVA_HOME="$JH"
  JAVAC="${JAVAC:-$JH/bin/javac.exe}"
  KEYTOOL="${KEYTOOL:-$JH/bin/keytool.exe}"
else
  JAVAC="${JAVAC:-javac}"
  KEYTOOL="${KEYTOOL:-keytool}"
fi

APP=app/src/main
OUT=build/release
NAME="PDZDH-Toolbox-1.2"

for f in "$BT/aapt2.exe" "$BT/zipalign.exe" "$PLATFORM"; do
  [ -e "$f" ] || { echo "missing: $f" >&2; exit 1; }
done

# 只清理上一轮产物，避免整目录递归删除
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"
rm -f "$OUT/unsigned.apk" "$OUT/aligned.apk" "$OUT/res.zip" \
      "$OUT/sources.txt" "$OUT/classes.txt" "$OUT/javac.log" "$NAME.apk" 2>/dev/null || true

echo "[1/6] aapt2 compile"
"$BT/aapt2.exe" compile --dir "$APP/res" -o "$OUT/res.zip"

echo "[2/6] aapt2 link"
"$BT/aapt2.exe" link \
  -o "$OUT/unsigned.apk" \
  -I "$PLATFORM" \
  --manifest "$APP/AndroidManifest.xml" \
  -R "$OUT/res.zip" \
  -A "$APP/assets" \
  --java "$OUT/gen" \
  --min-sdk-version 24 \
  --target-sdk-version 34 \
  --version-code 2 \
  --version-name 1.2-android \
  --auto-add-overlay

echo "[3/6] javac"
find "$APP/java" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
# --release 8 keeps lambda desugaring valid for minSdk 24. -parameters is required:
# d8 8.2.x throws an NPE on MethodParameters entries that carry no names.
if ! "$JAVAC" -J-Duser.language=en -J-Duser.country=US -encoding UTF-8 -nowarn \
     --release 8 -parameters -classpath "$PLATFORM;libs/*" -d "$OUT/classes" \
     @"$OUT/sources.txt" > "$OUT/javac.log" 2>&1; then
  cat "$OUT/javac.log" >&2
  echo "javac failed" >&2
  exit 1
fi

echo "[4/6] d8"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
for jar in libs/*.jar; do [ -e "$jar" ] && echo "$jar" >> "$OUT/classes.txt"; done
"$BT/d8.bat" --release --min-api 24 --lib "$PLATFORM" --output "$OUT/dex" @"$OUT/classes.txt"

echo "[5/6] package"
python - "$OUT/unsigned.apk" "$OUT/dex/classes.dex" <<'PY'
import sys, zipfile
apk, dex = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(dex, "classes.dex")
PY

echo "[6/6] align + sign"
"$BT/zipalign.exe" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

KS=keystore/pdzdh-release.jks
mkdir -p keystore
if [ ! -f "$KS" ]; then
  "$KEYTOOL" -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10950 \
    -dname "CN=PDZDH CalcuTool, O=PDZDH, C=CN" >/dev/null 2>&1
fi

"$BT/apksigner.bat" sign \
  --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey \
  --out "$NAME.apk" "$OUT/aligned.apk"

"$BT/apksigner.bat" verify --print-certs "$NAME.apk" | head -4
ls -lh "$NAME.apk"
echo "OK -> $(pwd)/$NAME.apk"
