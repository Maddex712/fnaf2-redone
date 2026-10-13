# Release verification

Seven JVM suites passed on 2026-10-13. APK alignment, archive CRC, API 26/35 manifest and v2/v3 signature verified.

The public APK certificate matches the existing standard 0.3.8 installation: `645de44157522f1f28413bfa25d026fae5437bf1fdcf4441dd7f3d2d8de0fae6`.

The helper extracted 804 images and 66 sounds from the supported owned Steam build and generated its personal ZIP. That ZIP remains private and is excluded from Git and release assets.

APK assets contain only the authored Puppet peek. No Steam image/audio pack, Windows font, signing key, private fixture or save is included. This exact APK has not been freshly playtested on a physical device.

SHA-256:

```text
3362214ec8835b41fa6a84a727ff73012ddc559e64fc466539caeb87ab6c9d7e  FNaF2-Redone-0.3.10.apk
7f25b61ff359d2f1d86c06de9cc83323a609d8dd3eaf11249c559d6c2d377947  FNaF2-Steam-Import-Helper.zip
```
