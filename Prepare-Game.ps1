param(
    [string]$GameExe = "C:/Program Files (x86)/Steam/steamapps/common/Five Nights at Freddy's 2/FiveNightsatFreddys2.exe",
    [switch]$OpenFolder
)
$ErrorActionPreference='Stop'
$project=Join-Path $PSScriptRoot 'tools/FusionAssetLite/FusionAssetLite.csproj'
$dotnet=(Get-Command dotnet -ErrorAction Stop).Source
if (!(Test-Path -LiteralPath $GameExe)) { throw 'Install FNaF 2 in Steam, or pass -GameExe with its executable path.' }
$sha=(Get-FileHash -LiteralPath $GameExe -Algorithm SHA256).Hash.ToLowerInvariant()
if ($sha -ne '84db9972c70ae02ba9596e1be624f2942545ee939611e7fcd593b75a67d41ac6') { throw "This executable differs from the inspected Steam build. Refusing to apply unverified image mappings. SHA256: $sha" }
$private=Join-Path $PSScriptRoot 'private-output'
New-Item -ItemType Directory -Force $private | Out-Null
& $dotnet run --project $project -c Release -- $GameExe $private --no-pack --no-shaders
if ($LASTEXITCODE -ne 0) { throw 'Game asset extraction failed' }
$data=Join-Path $private "Five Nights at Freddys 2"
$images=@(Get-ChildItem (Join-Path $data 'Images') -Filter '*.png')
$sounds=@(Get-ChildItem (Join-Path $data 'Sounds') -Filter '*.wav')
if ($images.Count -ne 804 -or $sounds.Count -ne 66) { throw 'Incomplete game data: expected 804 images and 66 sounds.' }
$manifest=@{format='fnaf2-steam-288';sourceSha256=$sha;images=804;sounds=66;createdBy='Local Steam importer'} | ConvertTo-Json
[IO.File]::WriteAllText((Join-Path $data 'pack.json'),$manifest,[Text.UTF8Encoding]::new($false))
Add-Type -AssemblyName System.IO.Compression.FileSystem
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
$pack=Join-Path $private "FNaF2-Steam-data-$stamp.zip"
[IO.Compression.ZipFile]::CreateFromDirectory($data,$pack,[IO.Compression.CompressionLevel]::Optimal,$false)
Write-Output "Your personal game data pack: $pack"
Write-Output 'Copy the ZIP to the Thor, open FNaF 2 Redone, and choose Settings > Import Steam data.'
Write-Output 'The APK and source contain no original game assets. Keep this data pack for your own device.'
if ($OpenFolder) { Start-Process explorer.exe -ArgumentList @('/select,',('"'+$pack+'"')) }
