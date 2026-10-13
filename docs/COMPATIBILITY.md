# Compatibility and testing

- Android API 26 minimum; API 35 target. 
- Designed and previously exercised on AYN Thor with office and cameras on separate displays.
- This release build runs seven JVM regression suites covering state, gameplay rules,
  event ordering, RNG, menu effects, death minigames and encounter catalog behavior.
- This exact public APK has not received a fresh on-device playthrough. Other devices,
  single-screen layouts, performance and display lifecycle cases are not certified.
- Native-game replay traces and private fixtures are excluded from this repository.
  Regression results do not establish perfect original-game parity.
- Public packaging uses Android’s monospace font instead of redistributing Windows Consolas.
  Optional uncredited legacy title images are excluded. The main lineup uses imported game data;
  the authored Puppet peek is retained. Gameplay simulation is unchanged by these packaging edits.
