param([ValidateSet('unit', 'real')][string]$Group = 'unit')
$root = Resolve-Path "$PSScriptRoot/.."
$build = Join-Path $root 'build/shell'
Remove-Item -LiteralPath $build -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $build | Out-Null
$main = Join-Path $build 'main'
$test = Join-Path $build 'test'
New-Item -ItemType Directory -Force $main,$test | Out-Null
javac --release 17 -Xlint:all -Werror -d $main (Get-ChildItem "$root/src/main/java" -Recurse -Filter *.java | ForEach-Object FullName)
Copy-Item "$root/src/main/resources/*" $main -Recurse -Force
$class = if ($Group -eq 'real') { 'FFLIntegrationTests' } else { 'UnitTests' }
javac --release 17 -Xlint:all -Werror -cp $main -d $test "$root/tests/java/io/fastfilelink/ffl/$class.java"
java -cp "$main;$test" "io.fastfilelink.ffl.$class"
exit $LASTEXITCODE
