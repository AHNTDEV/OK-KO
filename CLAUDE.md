# KO II Companion

Android companion app for the Teenage Engineering EP-133 K.O. II over USB-C. Owner: Martin. Current version: see `app/build.gradle.kts`. User manuals: `docs/MANUAL.md` (English), `docs/HANDBUCH.md` (German). Keep both in step with the app and with each other.

## Build and test

The machine has no system JDK or Gradle. Use Android Studio's JDK and the wrapper:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :protocol:test :audio:testDebugUnitTest :app:testDebugUnitTest
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

Toolchain: Android Studio 2026.2 (JDK 25), Gradle 9.8.2, AGP 9.5.0 with built-in Kotlin, Kotlin 2.4.21, compileSdk 37, minSdk 26. SDK is in `%LOCALAPPDATA%\Android\Sdk`, `local.properties` points to it (not committed).

Emulator: AVD `Medium_Phone_API_37.0`. Use `adb -s emulator-5554`. In Git Bash set `MSYS_NO_PATHCONV=1` before `adb push` to `/sdcard/...`. The emulator has no K.O. II, so only the offline UI and the sample editor (with a pushed WAV) can be checked there.

The release APK for testing is the debug build copied to `dist/` (ignored by git). It is signed with the debug key.

## Layout

- `:protocol` pure JVM. 7-bit packing, TE frames, FILE messages, `KoiiClient`, USB-MIDI codec. Unit-tested against `FakeDevice` in `src/test`.
- `:audio` Android library. WAV/AIFF decode, resampler, `AudioConverter`, `SampleOps` (editor operations), recorder and players.
- `:app` Compose UI. `usb/` raw bulk transport, `data/` repositories and `DeviceSession`, `midi/` pads and keys, `seq/` sequencer, `ui/` screens. State lives in `KoiiViewModel`; the object graph is `AppGraph` in `KoiiApp.kt`.

## Protocol facts that matter

Source: lexfrei/kokoko `docs/protocol.md`, probe runs on EP-133 os 2.5.1.

- Verified on hardware by that project: identity, GREET, INIT, LIST, metadata read, PUT, GET, DELETE.
- Not confirmed there: MOVE, PLAYBACK, metadata write, project download and upload, file events, the `active` field. Martin's first test of 0.3.0 on his device: everything worked at first glance, no detailed results.
- **Never add the GET "extra args" form.** A guessed layout preceded a firmware crash (code 69).
- Node ids are sparse (`/sounds` is 1000, `/projects` is 2000). Resolve paths by name. Slot number equals node id under `/sounds`.
- Settings (command 6) answer "command not found" on os 2.5.1: not implemented.

## Conventions

- UI text is English, sentence case, active verbs, no arrows and no "A · B" strings. Empty states say what to do. Capitals and the monospaced face are only for the display readout in the top bar.
- Two palettes: K.O. II (default) and Mono. Colours come from `Ink` and `Palettes`, never hard-coded in screens.
- Kotlin: match the surrounding code, small files, comments only where the reason is not obvious.
- Every pure logic change gets a unit test. Run all three test tasks before committing.
- Do not mention third-party competitor apps in code, docs or commit messages. The history was rewritten once to remove such references.

## Git and hosting

- Remote `origin` is `https://github.com/AHNTDEV/OK-KO` (private), branch `main`.
- Commit messages end with the co-author trailer from the session instructions.
- Ask before every push. Never force-push without Martin's explicit approval.

## Second brain (Anytype)

The project has a "Steckbrief" named "KO II Companion" in the Anytype space `second-brain`. Rules for that space are on its Schema page. Only metadata goes there (goal, status, home place, people), never code or manual text. After a milestone, update "Stand" on the Steckbrief and append a log entry in the format `[YYYY-MM-DD] action | title`.

## Open items

- Detailed hardware checks of the functions listed above.
- Drag-and-drop sorting of slots, arpeggiator sync to the device clock, editing the device's own patterns (file format undocumented), release signing.
