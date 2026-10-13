# Build the Android app

Install JDK 21, Android platform 35 and build-tools 35. Arrange `android.jar` under
`TOOLCHAIN/platform/` and the build-tools executables under `TOOLCHAIN/build/`;
the script discovers them recursively. Put the JDK bin directory on PATH.
Provide your own private signing key. Publisher signing keys are never distributed.

```powershell
$env:FNAF_SIGNING_PASSWORD = 'your signing password'
.\Build.ps1 -Toolchain 'C:\AndroidToolchain' -SigningKey 'C:\private\key.jks' -KeyAlias thor
```

Output: `build/FNaF2-Redone-0.3.10.apk`. The script runs seven regression suites,
compiles and aligns the APK, signs it and verifies its signature. No debug-mode
flag is used. A different key cannot update the publisher’s existing installation.

The helper requires .NET 10 SDK and restores its declared NuGet dependencies.
See `tools/FusionAssetLite/FusionAssetLite.csproj`. Build outputs, extracted data,
private keys and private source-game replay fixtures are excluded from Git.
