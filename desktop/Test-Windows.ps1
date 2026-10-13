param([Parameter(Mandatory=$true)][string]$PrivateDataDirectory)
$ErrorActionPreference='Stop'
$build=Join-Path $PSScriptRoot 'build';$cp="$build/classes;$build/deps/*;$build/tests"
New-Item -ItemType Directory -Force "$build/tests" | Out-Null
& javac --release 17 -encoding UTF-8 -cp $cp -d "$build/tests" "$PSScriptRoot/tests/WindowsInputTest.java"
if($LASTEXITCODE -ne 0){throw 'Windows tests compilation failed'}
& java '-Djava.awt.headless=true' -cp $cp dev.thor.fnaf2.WindowsInputTest $PrivateDataDirectory
if($LASTEXITCODE -ne 0){throw 'Windows input/render tests failed'}
