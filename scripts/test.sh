#!/usr/bin/env sh
set -eu
group=${1:-unit}
root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
build="$root/build/shell"; main="$build/main"; test="$build/test"
rm -rf "$build"
mkdir -p "$main" "$test"
javac --release 17 -Xlint:all -Werror -d "$main" $(find "$root/src/main/java" -name '*.java')
cp -R "$root/src/main/resources/." "$main/"
class=UnitTests; [ "$group" = real ] && class=FFLIntegrationTests
javac --release 17 -Xlint:all -Werror -cp "$main" -d "$test" "$root/tests/java/io/fastfilelink/ffl/$class.java"
exec java -cp "$main:$test" "io.fastfilelink.ffl.$class"
