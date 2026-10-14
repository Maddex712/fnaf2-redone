# FNaF 2 Redone 0.3.13 for Android

- Added **Settings → R2 release → Stay on cameras**. R2 still opens CAM 11 and winds the music box, but releasing it can now keep the monitor open for immediate camera selection. **Return to office** remains the default.
- The preference is saved, controller navigation includes the new setting, and R2 prompts describe the selected mode. Release during the monitor animation and interrupted input are covered by regression checks.
- Investigated the left-vent Toy Chica report. Tests confirm successful mask defense makes her retreat; ignoring her, raising the monitor and then lowering it produces her attack. Her AI has not been changed without a reproduced mismatch.

Validation: 18 focused winding/transition/interruption/Chica checks, all existing build regression suites and 13 recorded original-game scenarios passed. A separate QA application on the AYN Thor passed nine Android checks covering the setting, Activity restart, winding, monitor retention and camera switching. An Android-rendered Options image was inspected. This does not certify a complete physical-controller playthrough or whole-game parity.

Install the APK over the previous version; its signing certificate is unchanged. Keep the app installed to retain progress and imported game data. No Steam artwork/audio pack is bundled. First-time setup is described in [the installation guide](https://github.com/Maddex712/fnaf2-redone/blob/main/docs/INSTALL.md).

This release updates Android. The unchanged Windows 0.1.0 download remains on [v0.3.12](https://github.com/Maddex712/fnaf2-redone/releases/tag/v0.3.12).
