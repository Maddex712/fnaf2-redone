# Supported local Steam extraction

The importer reads `FiveNightsatFreddys2.exe`; it never executes it. It accepts SHA-256 `84db9972c70ae02ba9596e1be624f2942545ee939611e7fcd593b75a67d41ac6` only.

The verified image bank begins at byte 2,617,442 and ends at 189,032,448. It contains 804 length-checked zlib records. Images use 32-byte headers, RGB color-key transparency or a separate alpha plane. The sound bank begins at 189,036,292 and ends at 217,749,307, with 66 UTF-16-named, compressed WAV records. The resulting manifest identifies `fnaf2-steam-288`.

The JVM integration test compared every image pixel and every WAV byte against the independently extracted private reference. Android additionally checked selection MIME type, direct EXE import, image/sound counts, representative decoded pixels, rejection without replacing existing data, and staged-file cleanup.

To repeat the full integration check, compile `SteamImporter.java` and `tests/SteamImporterTest.java` with JDK 21, then run `dev.thor.fnaf2.SteamImporterTest <owned-exe> <private-reference-directory> <fresh-output-directory>`. The reference and output contain original artwork/audio: keep them private and out of Git. Different game builds are rejected rather than guessed.
