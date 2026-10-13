param([Parameter(Mandatory=$true)][string]$Toolchain,[Parameter(Mandatory=$true)][string]$SigningKey,[string]$KeyAlias='thor')
$ErrorActionPreference='Stop'
$Toolchain=(Resolve-Path $Toolchain).Path
$androidJar=Get-ChildItem (Join-Path $Toolchain 'platform') -Filter android.jar -Recurse | Select-Object -First 1 -ExpandProperty FullName
$aapt=Get-ChildItem (Join-Path $Toolchain 'build') -Filter aapt2.exe -Recurse | Select-Object -First 1 -ExpandProperty FullName
$buildTools=Split-Path $aapt
if (!$androidJar -or !$aapt) { throw 'Android platform 35 and build tools 35 are required. See README.' }
$jdk=Split-Path (Get-Command javac.exe).Source
$env:JAVA_HOME=Split-Path $jdk
$out=Join-Path $PSScriptRoot 'build'
New-Item -ItemType Directory -Force $out,(Join-Path $out 'classes'),(Join-Path $out 'dex'),(Join-Path $out 'tests') | Out-Null
function Check([string]$Name) { if ($LASTEXITCODE -ne 0) { throw "$Name failed with exit code $LASTEXITCODE" } }
$source=Join-Path $PSScriptRoot 'app/src/main'
& (Join-Path $jdk 'javac.exe') --release 8 -d (Join-Path $out 'tests') (Join-Path $source 'java/dev/thor/fnaf2/EventTimers.java') (Join-Path $source 'java/dev/thor/fnaf2/SourceRandom.java') (Join-Path $PSScriptRoot 'tests/SourceRandomTest.java') (Join-Path $source 'java/dev/thor/fnaf2/DreamSequence.java') (Join-Path $source 'java/dev/thor/fnaf2/ToyBonnieWalk.java') (Join-Path $source 'java/dev/thor/fnaf2/Fnaf2State.java') (Join-Path $source 'java/dev/thor/fnaf2/Fnaf2Catalog.java') (Join-Path $source 'java/dev/thor/fnaf2/ScarePlayback.java') (Join-Path $source 'java/dev/thor/fnaf2/ButtonChord.java') (Join-Path $source 'java/dev/thor/fnaf2/InputRouting.java') (Join-Path $PSScriptRoot 'tests/Fnaf2StateTest.java') (Join-Path $PSScriptRoot 'tests/GameplayParityTest.java')
Check 'Test compilation'
& (Join-Path $jdk 'java.exe') -cp (Join-Path $out 'tests') dev.thor.fnaf2.Fnaf2StateTest
Check 'State tests'
& (Join-Path $jdk 'java.exe') -cp (Join-Path $out 'tests') dev.thor.fnaf2.GameplayParityTest
Check 'Gameplay parity regression tests'
& (Join-Path $jdk 'javac.exe') --release 8 -cp (Join-Path $out 'tests') -d (Join-Path $out 'tests') (Join-Path $PSScriptRoot 'tests/EventOrderParityTest.java')
Check 'Event order test compilation'
& (Join-Path $jdk 'java.exe') -cp (Join-Path $out 'tests') dev.thor.fnaf2.EventOrderParityTest
Check 'Event order tests'
& (Join-Path $jdk 'java.exe') -cp (Join-Path $out 'tests') dev.thor.fnaf2.SourceRandomTest
Check 'Native RNG fixtures'
& (Join-Path $jdk 'javac.exe') --release 8 -d (Join-Path $out 'tests') (Join-Path $source 'java/dev/thor/fnaf2/MenuEffects.java') (Join-Path $PSScriptRoot 'tests/MenuEffectsTest.java')
Check 'Menu effect test compilation'
& (Join-Path $jdk 'java.exe') -cp (Join-Path $out 'tests') dev.thor.fnaf2.MenuEffectsTest
Check 'Menu effect tests'
& (Join-Path $jdk 'javac.exe') --release 8 -cp (Join-Path $out 'tests') -d (Join-Path $out 'tests') (Join-Path $source 'java/dev/thor/fnaf2/EventTimers.java') (Join-Path $source 'java/dev/thor/fnaf2/DeathMinigame.java') (Join-Path $source 'java/dev/thor/fnaf2/MinigameSprites.java') (Join-Path $PSScriptRoot 'tests/DeathMinigameTest.java')
Check 'Minigame test compilation'
& (Join-Path $jdk 'java.exe') -cp (Join-Path $out 'tests') dev.thor.fnaf2.DeathMinigameTest
Check 'Death minigame tests'
& (Join-Path $jdk 'javac.exe') --release 8 -cp (Join-Path $out 'tests') -d (Join-Path $out 'tests') (Join-Path $source 'java/dev/thor/fnaf2/EncounterCatalog.java') (Join-Path $PSScriptRoot 'tests/EncounterCatalogTest.java')
Check 'Encounter test compilation'
& (Join-Path $jdk 'java.exe') -cp (Join-Path $out 'tests') dev.thor.fnaf2.EncounterCatalogTest
Check 'Encounter and mask tests'

$manifest=Join-Path $source 'AndroidManifest.xml'
& $aapt link -I $androidJar --manifest $manifest -A (Join-Path $source 'assets') --min-sdk-version 26 --target-sdk-version 35 --version-code 22 --version-name '0.3.10-alpha' -o (Join-Path $out 'unsigned.apk')
Check 'Resource linking'
$files=Get-ChildItem (Join-Path $source 'java') -Recurse -Filter '*.java' | ForEach-Object FullName
& (Join-Path $jdk 'javac.exe') --release 8 -cp $androidJar -d (Join-Path $out 'classes') @files
Check 'Java compilation'
& (Join-Path $jdk 'jar.exe') --create --file (Join-Path $out 'classes.jar') -C (Join-Path $out 'classes') .
Check 'Class archive'
& (Join-Path $buildTools 'd8.bat') --min-api 26 --lib $androidJar --output (Join-Path $out 'dex') (Join-Path $out 'classes.jar')
Check 'DEX compilation'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$apk=[IO.Compression.ZipFile]::Open((Join-Path $out 'unsigned.apk'),[IO.Compression.ZipArchiveMode]::Update)
try { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($apk,(Join-Path $out 'dex/classes.dex'),'classes.dex') | Out-Null } finally { $apk.Dispose() }
& (Join-Path $buildTools 'zipalign.exe') -f 4 (Join-Path $out 'unsigned.apk') (Join-Path $out 'aligned.apk')
Check 'APK alignment'
$key=(Resolve-Path -LiteralPath $SigningKey).Path
if (!$env:FNAF_SIGNING_PASSWORD) { throw 'Set FNAF_SIGNING_PASSWORD for your private key.' }
$final=Join-Path $out 'FNaF2-Redone-0.3.10.apk'
& (Join-Path $buildTools 'apksigner.bat') sign --ks $key --ks-key-alias $KeyAlias --ks-pass env:FNAF_SIGNING_PASSWORD --key-pass env:FNAF_SIGNING_PASSWORD --out $final (Join-Path $out 'aligned.apk')
Check 'APK signing'
& (Join-Path $buildTools 'apksigner.bat') verify --verbose $final
Check 'APK signature verification'
Get-FileHash $final -Algorithm SHA256
