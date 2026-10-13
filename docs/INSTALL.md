# Download and install

## Already have the standard FNaF 2 Thor app?

Install the latest APK over it. Saves and imported data remain in app-private
storage. You do not need to import assets again. Keep the app installed throughout
the update. The developer app (`dev.thor.fnaf2.dev`) is a separate installation.

## First installation

1. Download **FNaF2-Redone-0.3.10.apk** from Releases on your Android device.
2. Open it and allow your browser/file manager to install APKs when Android asks.
3. On Windows, install the [.NET 10 SDK](https://dotnet.microsoft.com/en-us/download/dotnet/10.0).
   The SDK is required to build and run the included personal-import helper.
4. Download and extract **FNaF2-Steam-Import-Helper.zip**. Keep its folders together.
5. In Steam, open FNaF 2 → Manage → Browse local files. Locate `FiveNightsatFreddys2.exe`.
6. Open PowerShell in the extracted helper folder and run:

```powershell
powershell -ExecutionPolicy Bypass -File .\Prepare-Game.ps1 -GameExe "C:/path/to/FiveNightsatFreddys2.exe"
```

The helper checks the supported Steam executable, reads it locally, and creates
a personal ZIP in the helper’s `private-output` folder. It does not upload it.
Leave sufficient free space for extracted files and the ZIP; extraction can take time.

7. Copy the generated ZIP to your Android device’s Downloads folder using USB,
   your own private file transfer, or your own private cloud storage.
8. Open FNaF 2 Redone → Settings → **Import Steam data**, and select the ZIP.
9. Wait for successful import, then select **New Game**.

An existing `fnaf2-steam-288` data pack works too. The Android importer takes a
ZIP, not the Steam executable. No Steam login is required inside the app.

## Troubleshooting

- **Unsupported executable:** this release accepts SHA-256
  `84db9972c70ae02ba9596e1be624f2942545ee939611e7fcd593b75a67d41ac6` only.
  Do not bypass the check for a different edition.
- **Import file missing:** browse Downloads instead of Recent in the file picker.
- **Helper won’t run:** verify .NET 10 SDK is installed with `dotnet --list-sdks`.
- **Installation conflict:** retain saves; do not uninstall reflexively. Check that
  you are updating the standard app with this publisher’s APK.
- **Wrong screens:** check the app’s display/settings options.

The public files contain no Steam artwork/audio pack or Windows fonts. Keep your
personal data ZIP private. Uninstalling or clearing app storage removes progress.
