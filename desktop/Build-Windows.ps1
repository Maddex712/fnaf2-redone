param([switch]$Package,[string]$Python='python')
$ErrorActionPreference='Stop'
$out=Join-Path $PSScriptRoot 'build';$deps=Join-Path $out 'deps';$classes=Join-Path $out 'classes'
New-Item -ItemType Directory -Force $deps,$classes | Out-Null
function Check {if($LASTEXITCODE -ne 0){throw "Windows build failed ($LASTEXITCODE)"}}
$downloads=@(
 @{File='jna-5.14.0.jar';Hash='34ed1e1f27fa896bca50dbc4e99cf3732967cec387a7a0d5e3486c09673fe8c6';Url='https://repo.maven.apache.org/maven2/net/java/dev/jna/jna/5.14.0/jna-5.14.0.jar'},
 @{File='json-20240303.jar';Hash='3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed';Url='https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar'}
)
foreach($d in $downloads){if(!(Test-Path "$deps/$($d.File)")){Invoke-WebRequest $d.Url -OutFile "$deps/$($d.File)"};if((Get-FileHash "$deps/$($d.File)" -Algorithm SHA256).Hash -ne $d.Hash){throw 'Dependency checksum mismatch'}}
& $Python -B "$PSScriptRoot/generate.py";Check
$sources=@(Get-ChildItem "$PSScriptRoot/src","$out/generated" -Recurse -Filter '*.java' | ForEach-Object FullName)
& javac --release 17 -encoding UTF-8 -cp "$deps/*" -d $classes @sources;Check
New-Item -ItemType Directory -Force "$classes/assets" | Out-Null
Copy-Item "$PSScriptRoot/../app/src/main/assets/menu-puppet-peek.png" "$classes/assets"
New-Item -ItemType Directory -Force "$out/input" | Out-Null
& jar --create --file "$out/input/fnaf2-redone.jar" --main-class dev.thor.fnaf2.MainActivity -C $classes .;Check
Copy-Item "$deps/*.jar" "$out/input"
if($Package){
 $dest=Join-Path $out ('dist-'+(Get-Date -Format 'yyyyMMdd-HHmmss'));New-Item -ItemType Directory -Force $dest | Out-Null
 & jpackage --type app-image --name 'FNaF 2 Redone' --app-version ((Get-Content "$PSScriptRoot/VERSION" -Raw).Trim()) --dest $dest --input "$out/input" --main-jar fnaf2-redone.jar --main-class dev.thor.fnaf2.MainActivity --java-options '-Xmx768m' --add-modules java.base,java.desktop,java.logging,java.prefs,java.management,jdk.unsupported,jdk.crypto.ec --jlink-options '--strip-debug --no-man-pages --no-header-files';Check
 $zip=Join-Path $out ('FNaF2-Redone-Windows-'+(Get-Content "$PSScriptRoot/VERSION" -Raw).Trim()+'.zip');Compress-Archive -Path "$dest/FNaF 2 Redone" -DestinationPath $zip -Force;Write-Output $zip
}
