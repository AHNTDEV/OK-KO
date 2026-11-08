# KO II Companion

Android companion for the Teenage Engineering EP-133 K.O. II over USB-C: sample manager, backup/restore, MIDI pads, USB audio recorder. Monochrome TE look.

Target device: K.O. II, firmware 2.5.1. The app checks `os_version` from GREET against 2.0.5 and only enables USB audio from 2.5.0.

## Layout

| Module | Kind | Contents |
| --- | --- | --- |
| `:protocol` | pure JVM | 7-bit packing, TE frames, FILE messages, `KoiiClient`, USB-MIDI packet codec, name normalisation. No Android dependency, unit-tested against an in-memory device simulator (`FakeDevice`). |
| `:audio` | Android library | WAV/AIFF decode, windowed-sinc resampler to 46875 Hz, `AudioConverter` (fast path for s16/46875 Hz WAV), `MediaDecoder` fallback (mp3, m4a, flac, ogg), `UsbAudioRecorder`, `UsbAudioPlayer`, `WavStreamWriter`. |
| `:app` | Android app | Compose UI, USB discovery and bulk transport, repositories (sounds, trash, backup, pads), MIDI controller, transfer queue plus foreground service. |

Differences from the original blueprint:

- `UsbMidiTransport` lives in `:app` (it needs `android.hardware.usb`); `:protocol` only sees the `MidiTransport` interface. That keeps `:protocol` testable on a plain JVM.
- The USB **audio** interface is not claimed through `UsbManager`. Android's own USB audio driver owns it. The recorder picks the device via `AudioManager` (`TYPE_USB_DEVICE`) and `setPreferredDevice`. Claiming the audio interface would take it away from the system.
- `AudioRecord` cannot open with an unspecified rate; the recorder reads `AudioDeviceInfo.sampleRates` and prefers 46875, then 48000. Recordings are stored at that rate; "Use last take as sample" resamples to 46875 Hz / s16 on upload.

## Build

Built and tested with Android Studio 2026.2 (bundled JDK 25), Gradle 9.8.2, AGP 9.5.0, Kotlin 2.4.21, compileSdk 37. Needs the Android SDK platform 37.

```bash
./gradlew :protocol:test :audio:testDebugUnitTest
./gradlew :app:assembleDebug
```

Create `local.properties` with `sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk` if Android Studio did not.

## Hardware verification checklist

Taken from `docs/protocol.md` and `docs/conformance.md` of lexfrei/kokoko (probe runs on an EP-133, os 2.5.1, 2026-10-30).

Verified there: identity, GREET, INIT, LIST, METADATA read, PUT (device assigns `NNN.pcm`), GET (byte-identical), DELETE.

**Not verified on hardware, test these first, with a sacrificial sample:**

1. Our raw USB-MIDI framing (4-byte packets, cable 0) and the 17-byte identity reply over bulk endpoints. kokoko used CoreMIDI.
2. INIT `chunk_size` and the resulting chunk length (`maxChunkPayloadLength`); CRC of a downloaded sample.
3. METADATA write (rename, root note, loop points); required by upload.
4. `MOVE` (slot sort and swap). The factory sounds report no move permission; uploads use `FileFlags.USER_SOUND` (adds MOVE and PLAYBACK) and fall back to `FACTORY_SOUND` if the device refuses.
5. `PLAYBACK` preview (offset 0, 1000 ms). **Do not** add the GET "extra args" form: a guessed layout preceded a firmware crash (code 69) on os 2.5.1. `FileMsg.getInit` only sends the offset.
6. Project download (`GET` on `/projects/Pxx`) and upload of `Pxx.tar` to `/projects` (60 s timeout).
7. File events (INIT subscribe): payload offset of events, FILE_ADDED/UPDATED/DELETED/MOVED.
8. Pad path `/projects/Pxx/groups/A..D/pads/01..12` and `{"sym": id}`.
9. Slot number == node id under `/sounds`; the root node for path resolution is assumed to be 0.
10. USB audio: sample rates Android reports for the K.O. II, and whether `RECORD_AUDIO` is enough.
11. MIDI channel the pads listen on (setting: MIDI channel, default 1).

Settings (command 6) are not implemented: the device answers "command not found" on os 2.5.1.

## 0.2.0

- Pixel-art cassette launcher icon (adaptive vector, `app/src/main/res/drawable/ic_launcher_foreground.xml`).
- Themes: K.O. II (default) and Mono. Palette switch in Settings.
- Optional **loop folder** (Settings, system folder picker, no storage permission). Pads → Assign → Loops: audition on the phone, then convert, upload to the next free slot and put it on the pad.
- Pads: project 1–9 and group A–D, sample names on the pads, group colours.
- Sounds: search by name or slot, long-press multi-select (bulk delete, bulk export to a folder), sound editor with waveform and metadata sliders (root note, pitch, pan, volume, attack, release, BPM).
- Keys: on-screen keyboard with chords (maj/min/sus/dim, 6/7/9, low octave) and arpeggiator (4 modes, 6 rates, 3 octaves) on the MIDI channel from Settings. Tempo is the app BPM, not yet locked to the device clock.
- Library tab: projects, backup/restore, trash.

## 0.3.0

- **Beats**: step sequencer for all 48 pads (groups A–D), 8 to 32 steps, 8 pattern slots, tempo, swing, accents. The phone plays the pads over MIDI; optionally it also sends MIDI start and clock so the K.O. II follows. Patterns export as standard MIDI files or pattern files (JSON) and import from both (notes C2 to B5 map to pads A to D).
- **Sample editor** (Sounds tab: Edit sample, or Editor to open a file from the phone): waveform with a draggable selection, crop, cut out, silence, trim, reverse, fades, normalize, gain, mono, repitch by semitones, undo, preview. Save as a new sound, replace the original in place (the old version goes to the app trash and the slot keeps its settings), or export a WAV.
- **Device control**: Pads can switch the K.O. II to the shown project and group by writing the `active` field described in the community protocol notes (not yet checked on a real K.O. II).
- Slate theme removed; K.O. II is the default.

## Design

Applied the frontend-design guidance: the top bar is an inset readout after the K.O. II's own display (the only monospaced, capitalised text), pads carry large numerals like the silk-screen on the hardware, titles and keys use the system condensed face and running text the system sans. Text is sentence case, section headings are separated by a rule instead of coloured capitals, and radii follow role (pads large, keys medium, chips round). Palettes: K.O. II and Mono.

## Roadmap status

- M1–M3, M5–M7: implemented.
- M4: MOVE/DELETE client, tap-to-move/swap grid (12 × 4 per page, 4 columns), app trash. Drag-and-drop sorting is not implemented.
- Navigation (`KoiiViewModel`: tab + sub-screens), `SoundRepository` cache, `TransferManager` + `TransferService`: implemented.
- Not done: editing the K.O. II's own on-device patterns (the pattern file format is not documented; the Beats sequencer runs on the phone), drag-and-drop sorting, arpeggiator sync to the device clock, release signing.
