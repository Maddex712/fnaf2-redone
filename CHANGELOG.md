# Release notes

## 0.3.13 — configurable music-box quick access

- Added a saved R2-release choice: return to office or stay on cameras. Default controls are retained.
- Added matching camera/control prompts and ten-option controller/touch settings navigation.
- Verified interrupted/early-release winding and masked/unmasked left-vent Chica paths; no speculative AI change.
- Passed 18 focused checks, 13 original replay scenarios and nine isolated Android runtime checks on Thor.


## 0.3.12 — Steam EXE setup and first Windows build

- Android now selects and scans the Steam EXE directly, with ZIP support retained. Fixed the ZIP-only file-picker restriction reported by a tester.
- Added offline extraction, progress messages, explanatory setup, completion/error dialogs, strict executable validation, and staging cleanup. Failed imports preserve installed data.
- Added automatic mouse/keyboard/controller profiles and PC-style pointer, hover and Ctrl controls. Existing controller mappings remain.
- Corrected the mouse-profile monitor/mask bar graphics.
- Added Windows 0.1.0: standalone portable application with bundled Java, single-screen gameplay, Steam-library discovery, EXE/ZIP setup, keyboard/mouse and XInput support, fullscreen and focus-loss handling.
- Android import tests passed on Thor; full importer verification matched 804 image pixel comparisons and 66 WAV byte comparisons. Windows input/render tests and a live-window gameplay smoke test passed.


## 0.3.10 — first public FNaF 2 Redone package

Includes the FNaF 2 Android runtime:
campaign and Custom Night, mask/cameras/music box, main-menu rare gallery and Arcade,
controller minigames, office plushies, 1987 lower-screen interface, perspective,
manual camera panning, and Halloween decoration option.

Public packaging changes: Android monospace font, no bundled Windows font,
no uncredited legacy title images, release-mode APK, FNaF 2 repository link,
and standalone source-based Steam ZIP preparation helper.

This is a regular GitHub Release containing an alpha-quality reconstructed runtime.
Exact original-game parity and this APK’s fresh hardware validation remain incomplete.
