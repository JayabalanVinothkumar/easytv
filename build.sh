#!/bin/bash
# Builds GlassHome.apk without Gradle. Needs: JDK, aapt2, android.jar (API 34), d8 + its libraries, python3 with 'cryptography'.
set -e
cd "$(dirname "$0")"
T=${TOOLS:-/home/claude/dl/tools}
AAPT2=${AAPT2:-/home/claude/dl/aapt2whl/aapt2/bin/Linux/aapt2}
VERSION_CODE=${VERSION_CODE:-1}; VERSION_NAME=${VERSION_NAME:-1.0}
rm -rf build && mkdir -p build/gen build/classes build/dex signing
# Gradle takes the package name from build.gradle; aapt2 needs it in the manifest, so add it to a copy.
sed 's|<manifest |<manifest package="com.vj.glasshome" |' AndroidManifest.xml > build/AndroidManifest.xml
$AAPT2 compile --dir res -o build/res.zip
$AAPT2 link -o build/base.apk -I $T/android.jar --manifest build/AndroidManifest.xml --java build/gen \
  --min-sdk-version 26 --target-sdk-version 34 --version-code $VERSION_CODE --version-name $VERSION_NAME build/res.zip
javac -nowarn -source 8 -target 8 -bootclasspath $T/android.jar -d build/classes $(find src build/gen -name '*.java') 2>&1 | grep -v -e JAVA_TOOL_OPTIONS -e 'bootstrap class path' -e 'warning: \[options\]' -e '^[0-9]* warnings\?$' || true
CP=$(ls $T/*.jar | grep -v android.jar | tr '\n' ':')
mkdir -p build/tools && javac -nowarn -cp "$CP" -d build/tools tools/StripParams.java 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
java -cp "$CP:build/tools" StripParams $(find build/classes -name '*.class') 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
java -cp "$CP" com.android.tools.r8.D8 --lib $T/android.jar --min-api 26 --release --output build/dex $(find build/classes -name '*.class') 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
python3 tools/pack_sign.py build/base.apk build/dex/classes.dex build/GlassHome.apk signing/key.pem signing/cert.pem
