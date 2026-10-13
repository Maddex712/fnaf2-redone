# Windows version

1. Download **FNaF2-Redone-Windows-0.1.0.zip** from Releases.
2. Extract the whole ZIP. Open the **FNaF 2 Redone** folder and run **FNaF 2 Redone.exe**. Keep the accompanying `app` and `runtime` folders together. Java is bundled; no Android emulator or Java installation is required.
3. Choose **Game → Scan / import Steam copy**. Scan your Steam libraries, or select `FiveNightsatFreddys2.exe` yourself. An existing FNaF 2 data ZIP also works. Setup runs locally and does not launch the Steam executable.
4. After setup finishes, select **New Game**.

The office, tablet, mask, menus and minigames use one window. F11 toggles fullscreen. Move the mouse to the office edges to look; hover the white/red bottom bars for the monitor/mask. Click camera buttons, hold a vent-light button, or hold the music-box winding button. Hold Ctrl for the flashlight; Esc pauses. Minigames support WASD and arrow keys.

XInput controllers automatically select the controller profile. Existing Android mappings are retained: X monitor, B mask, A flashlight, L1/R1 vent lights, R2 quick winding, D-pad cameras, left stick office and right stick camera pan. Neutral controller events do not override the mouse. Focus loss pauses play and clears held inputs.

Progress, settings and imported data live in `%LOCALAPPDATA%/FNaF2Redone`, separate from FNaF 1, Android, and the original Steam game. For an update, replace the extracted application folder while retaining that data directory. Uninstalling the original Steam game after setup does not remove the imported data.

This is the first Windows build. A real Windows window was tested through night startup, flashlight, tablet/camera, pause and shutdown; 21 additional input/render/focus checks passed. Physical XInput hardware and extended full-night Windows play remain unverified. The launcher is unsigned, so Windows may show a reputation warning.

## Build from source

Install JDK 21, Python 3 and PowerShell. From the repository root:

```powershell
./desktop/Build-Windows.ps1 -Package -Python python
```

The build downloads two checksum-pinned dependencies and generates the shared runtime/view classes from the Android source. The package is written to `desktop/build/FNaF2-Redone-Windows-0.1.0.zip`. No Steam files or game-data pack are bundled.

For private testing, put your imported data under a fresh directory's `game` folder, then run `desktop/Test-Windows.ps1 -PrivateDataDirectory <fresh-directory>`. Never use an existing player save directory for tests. Screenshots are saved there and must remain private unless intentionally shared.
