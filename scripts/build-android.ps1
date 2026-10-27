# Builds the slim JAR an Android app embeds: the compiled classes only, without the bundled
# apebind/ffl.com. The app ships the core itself (jniLibs/libffl.so) and points
# APERuntime.builder().binary(...) at it, so the 48 MB resource would only bloat the APK.
param([string]$Output = '')
$root = Resolve-Path "$PSScriptRoot/.."
$version = ([xml](Get-Content "$root/pom.xml")).project.version
if ($Output -eq '') { $Output = Join-Path $root "build/android/ffl-java-$version.jar" }
$classes = Join-Path $root 'build/android/classes'
Remove-Item -LiteralPath $classes -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $classes, (Split-Path -Parent $Output) | Out-Null
javac --release 17 -Xlint:all -Werror -d $classes (Get-ChildItem "$root/src/main/java" -Recurse -Filter *.java | ForEach-Object FullName)
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
jar --create --file $Output -C $classes .
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "Wrote $Output"
