# Compatibility and testing

- Android API 26 minimum; API 35 target. 
- Designed and previously exercised on AYN Thor with office and cameras on separate displays.
- This release build runs eight JVM regression suites covering state, gameplay rules,
  event ordering, RNG, menu effects, death minigames and encounter catalog behavior.
- APK 0.3.12 was installed on AYN Thor. Fifteen EXE import/picker/rollback checks and nineteen input/display checks passed. Normal launch succeeded. These are targeted checks, not a complete-night playthrough or certification of other Android devices.
- Native-game replay traces and private fixtures are excluded from this repository.
  Regression results do not establish perfect original-game parity.
- Public packaging uses Android’s monospace font instead of redistributing Windows Consolas.
  Optional uncredited legacy title images are excluded. The main lineup uses imported game data;
  the authored Puppet peek is retained. Gameplay simulation is unchanged by these packaging edits.

- Windows 0.1.0 uses the same simulation and a Java2D/Swing host. Twenty-one input/render/focus checks and a live native-window night/flashlight/camera/pause/shutdown test passed on Windows 11. The bundled launcher also opened and closed successfully. Physical XInput hardware and long Windows sessions remain unverified. See [Windows instructions](WINDOWS.md).
