#!/usr/bin/env sh
# Builds the slim JAR an Android app embeds: the compiled classes only, without the bundled
# apebind/ffl.com. The app ships the core itself (jniLibs/libffl.so) and points
# APERuntime.builder().binary(...) at it, so the 48 MB resource would only bloat the APK.
set -eu
root=$(cd "$(dirname "$0")/.." && pwd)
version=$(sed -n 's#^  <version>\(.*\)</version>#\1#p' "$root/pom.xml" | head -n 1 | tr -d '\r')
output=${1:-"$root/build/android/ffl-java-$version-android.jar"}
classes="$root/build/android/classes"
rm -rf "$classes"
mkdir -p "$classes" "$(dirname "$output")"
find "$root/src/main/java" -name '*.java' | sort > "$root/build/android/sources.txt"
javac --release 17 -Xlint:all -Werror -d "$classes" @"$root/build/android/sources.txt"
jar --create --file "$output" -C "$classes" .
echo "Wrote $output"
