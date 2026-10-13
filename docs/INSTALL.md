# Download and install

## What you need

- Android 8.0 or newer; AYN Thor is the target handheld.
- Your purchased Windows Steam copy of Five Nights at Freddy’s 2.
- Access to your purchased Steam installation to copy its EXE.
- A way to transfer the EXE or an existing data ZIP to Android.

The APK contains the Android application. Your Steam copy supplies the original
images and sounds through a local import. No Steam account sign-in is required.

## First installation

1. Download **FNaF2-Redone-0.3.12.apk** from Releases and install it on Android.
2. In Steam on your PC, open FNaF 2 → Manage → Browse local files. Locate `FiveNightsatFreddys2.exe`.
3. Copy that EXE to Downloads on your Android device using your own file transfer.
4. Open FNaF 2 Redone → **Settings → Scan Steam file** (or select setup on the initial screen).
5. Choose **Select Steam file**, browse to Downloads, and select the EXE. Android's picker now accepts EXEs rather than filtering for ZIPs only.
6. Wait while the app prepares 804 images and 66 sounds locally. After **Ready to play**, select **New Game**.

No PC extraction helper or .NET SDK is needed for this route. Keep sufficient free space for the copied EXE, temporary extraction and installed game data. Your EXE is read as data; it is never launched on Android. No Steam sign-in is required.

Existing personal FNaF 2 data ZIPs still work in the same picker. The optional import helper remains available for people who prefer preparing a ZIP on a PC.

## Troubleshooting

- **Unsupported executable:** this release accepts SHA-256
  `84db9972c70ae02ba9596e1be624f2942545ee939611e7fcd593b75a67d41ac6` only.
  Do not bypass the check for a different edition.
- **Import file missing:** browse Downloads instead of Recent in the file picker.
- **Helper won’t run:** verify .NET 10 SDK is installed with `dotnet --list-sdks`.
- **Installation conflict:** retain saves; do not uninstall reflexively. Check that
  the APK came from this repository.
- **Wrong screens:** check the app’s display/settings options.

The public files contain no Steam artwork/audio pack or Windows fonts. Keep your
personal data ZIP private. Uninstalling or clearing app storage removes progress.

## Future updates

Install a newer APK over the game without uninstalling it. Progress and imported
data remain in place, so the Steam import is needed only for initial setup.
Uninstalling or clearing app storage deletes that data.
