# KO II Companion: User manual

Version 0.3.0. The app controls the Teenage Engineering EP-133 K.O. II over USB-C: manage and edit samples, assign pads, program beats, play chords, record and back up.

A German version is in [HANDBUCH.md](HANDBUCH.md). Button names in this manual are quoted exactly as they appear in the app.

## 1. Getting started

**What you need**
- An Android phone (Android 8 or newer) with USB host support.
- An EP-133 K.O. II with firmware 2.0.5 or newer. USB audio (the recorder) needs firmware 2.5.
- A USB-C **data** cable. Charge-only cables do not work.

**Connecting**
1. Switch the K.O. II on and connect it to the phone with the cable.
2. The first time, Android asks for USB permission. Allow it.
3. The display bar at the top then shows, for example, "K.O. II FW 2.5.1" and the free memory. If it says "No device", the device was not found. See section 13.

Tap the display bar to open the device page with serial number, firmware, mode, memory and the device log. "Settings" sits next to it.

**How the app is laid out**
Six tabs at the bottom: Pads, Beats, Sounds, Keys, Rec, Library. Longer jobs (upload, backup, export) keep running in the background, with a progress bar at the bottom and a notification. Messages and errors appear in a banner above the tabs. "OK" dismisses it.

## 2. Pads

This shows the pads of a project, laid out like the hardware (10 11 12 at the top, 1 2 3 at the bottom).

- **Project 1 to 9** and **Group A to D** choose what is shown. Each pad shows its number and the name of its sample.
- **Play pads**: tapping a pad triggers it on the K.O. II (MIDI note, volume from the "Velocity" slider). Holding keeps the note on.
- **Assign**: tap a pad and assign a sample to it (see below).
- **Transport**: "Play", "Stop" and "Cont" send MIDI start, stop and continue. "+" and "-" set the tempo, "Clock" sends MIDI clock at that tempo.
- **Show this project and group on the K.O. II**: with the box ticked, the device switches to the project and group you choose in the app. This function has not been checked in detail yet.

**Assigning a sample to a pad**
1. Choose "Assign" and tap the pad.
2. "Device sounds" at the top: the sounds on the device, with a filter. "Play" auditions a sound, "Assign" assigns it.
3. "Loops" at the top: your loops from the phone (see section 8). "Play" auditions them on the phone. "Put on pad" converts the loop, uploads it to the next free slot and puts it on the pad.

## 3. Sounds

This is where you manage the device's 999 slots. A page shows 48 slots. Use "<" and ">" to page.

- **Search**: the field at the top searches by name or slot number.
- **Select**: tap a slot. The actions appear below.
- **Multi-select**: press and hold a sound, then tap more. Available: "Delete", "Export" (as WAV into a folder of your choice) and "Clear".

**Actions for one sound**
- **Play**: plays a short preview on the device.
- **Move**: pick the sound, then tap the target slot. If it is occupied, the two sounds swap places.
- **Edit**: opens the detail page (section 4).
- **Save**: saves the sound as a WAV file.
- **Delete**: deletes the sound on the device. A copy stays in the app trash (section 9).
- **Upload**: uploads audio files (section 5).

## 4. Sound detail and sample editor

**Detail page** (Sounds, then Edit)
- The sound's waveform, plus "Play", "Rename" and "Save WAV".
- Sliders for Root note, Pitch, Pan, Volume, Attack, Release and BPM. "Save changes" writes only the values you changed. "Reset" discards the changes.
- "Edit sample" opens the sample editor with this sound.

**Sample editor**
Open it with "Edit sample" (a sound from the device), or with "Editor" at the top of the Sounds tab, where "Open a file from your phone" takes WAV, AIFF, MP3, FLAC, M4A and OGG.

- **Selection**: drag across the waveform to mark a part. The handles at the edges can be dragged, a tap clears the selection. "Select all" marks everything.
- **Cut and shape**: "Crop" keeps only the selection, "Cut out" removes it, "Silence" mutes it, "Trim" cuts silence from the start and end. "Reverse", "Fade in", "Fade out" and "Mono" do what they say.
- **Level**: "Normalize" raises the level to the maximum, "Gain -3 dB" and "Gain +3 dB" change it (on the selection if there is one).
- **Pitch in semitones**: -12, -1, +1, +12. As on a sampler, the sample plays faster or slower, so it gets shorter or longer.
- **Play** plays the selection or the whole sample on the phone. **Undo** steps back (up to 20 steps).
- **Save**
  - "Replace on device" replaces the original in the same slot. The slot number and its settings (root note, pitch and so on) are kept. The old version goes to the trash.
  - "Save as new" puts the result in the next free slot.
  - "Export WAV" saves a WAV file on the phone.
  - "Rename" changes the name.

The K.O. II takes at most 40 seconds. If your sample is longer, shorten it first. Until then "Replace on device" and "Save as new" are disabled.

## 5. Uploading files

1. Sounds, then "Upload", then choose one or more audio files.
2. The upload page shows each file's name, its length and whether it is "sent as is" (already in the device format) or "converted to 46875 Hz". Errors come with a reason, for example too long or more than two channels.
3. Set "Start slot" to decide where filling begins, and tap "Send … file(s)".

The app converts everything to 16 bit and 46875 Hz. Mono and stereo are kept, more than two channels are not possible. Names are shaped for the device: no extension, lower case, at most 16 characters.

## 6. Beats (sequencer)

A step sequencer that plays the K.O. II's pads from the phone.

- **Pattern 1 to 8**: eight memory slots. Changes are saved automatically.
- **Play** starts, "Stop" ends. Set the tempo with "-" and "+", and **Swing** with the slider (it delays every second step).
- **8, 16, 24, 32 steps**: the pattern length. One step is a sixteenth note.
- **Group A to D**: which twelve pads the grid shows. All four groups sound at the same time.
- **Grid**: tap a step once for a hit, twice for an accent (louder, with a dot), three times to clear it. Tap a pad number on the left to hear the pad, press and hold to clear its row.
- **Also send MIDI start and clock to the K.O. II**: off, the phone plays the pads by itself. On, the device also gets start and tempo, so its own sequencer follows.
- **Clear** empties the pattern, **Copy to next** copies it to the next slot.
- **Export MIDI** saves the pattern as a MIDI file, **Export file** as a pattern file (JSON). **Import MIDI or pattern file** loads either into the selected slot and replaces it. MIDI notes C2 to B5 are mapped to pads A to D, other notes are skipped.

The sequencer runs on the phone. The app cannot read or change the K.O. II's own patterns.

## 7. Keys (chords and arpeggiator)

A keyboard over two octaves that sends notes to the K.O. II.

- **Note, Maj, Min, Sus, Dim**: a single note or a triad.
- **6, 7, 9**: adds an extension. **Low** adds the lower octave.
- **Arp**: plays the chord as an arpeggio. Choose a mode (Up, Down, Up/Dn, Rnd), a rate from 1/4 to 1/32 and 1 to 3 octaves. The tempo comes from the BPM setting.
- "-" and "+" next to the range shift the octave.

Notes go out on the MIDI channel from the settings. The K.O. II must listen on that channel.

## 8. Loop folder

Under Settings, "Loop folder (optional)", choose a folder on the phone that holds your loops. The app reads it through the system picker and needs no storage permission. Subfolders up to three levels deep are included. "Rescan" reads it again, "Change" picks another folder, "Clear" removes it.

The loops appear when you assign a pad (Pads, Assign, Loops), see section 2.

## 9. Trash

Deleted sounds sit in the app's trash (Sounds, "Trash", or Library, "Trash").

- **Restore** puts a sound back into its old slot, or into the first free one if the slot has been taken in the meantime.
- **Remove** deletes it for good, **Empty trash** clears everything.

The trash lives in the app's own storage only. If you clear the app data or remove the app, it is gone.

## 10. Library: projects and backup

**Projects** (P01 to P09): "Tar" saves a project as a `.tar` archive.

**Backup**
1. Tick "Sounds (as WAV)" and/or "Projects (as tar)".
2. Tap "Backup" and choose where to save. The result is a zip file with an overview (`meta.json`, without the serial number), the sounds as WAV with their settings and the projects as tar.
3. A message lists anything skipped, for example a project whose archive was not valid.

**Restore**
1. Tap "Restore" and choose the zip file.
2. The app shows what is inside and lets you untick sounds and projects separately.
3. Without "Replace what is already on the device", occupied slots and existing projects are skipped. With it ticked, they are overwritten.
4. "Restore" starts. Each file is checked against its checksum, damaged ones are skipped.

Make a backup before bigger changes.

## 11. Recorder

Only with firmware 2.5 or newer.

- **Record K.O. II output**: records the device's stereo output over USB. You choose the WAV file as the target. The first time, Android asks for the microphone permission, which also covers USB inputs. "Stop" ends the recording.
- **Bit depth**: 16, 24 or 32 float.
- **Save last take as sample**: converts the recording (up to 40 seconds) and puts it in the next free slot.
- **Play from your phone into the K.O. II**: "Play file" plays a file from the phone into the device's USB input. "Stop" ends it. This is how you sample material from the phone.

## 12. Settings

- **Theme**: "K.O. II" (light, orange) or "Mono" (dark, grey).
- **Loop folder**: see section 8.
- **MIDI channel**: the channel for pads, keys and beats. The K.O. II must listen on it.
- **Pad velocity**: the volume of pads and keys.
- **Recording bit depth**: the default for recordings.

## 13. If something does not work

| Problem | What you can do |
| --- | --- |
| "No device" | Try another cable (a data cable!), reconnect the device, use "Scan again" on the device page. |
| "Allow USB access" | Confirm the permission in the Android prompt. |
| "Error, tap for details" | Tap the bar. It states the reason, for example firmware too old or not a K.O. II. |
| Timeout | The device was busy. Wait a moment and try again, reload the list if needed. |
| "No free slot" | All 999 slots are taken. Delete sounds first. |
| Sound too long | Shorten it to at most 40 seconds, best in the sample editor. |
| Nothing audible from pads, keys or beats | Compare the MIDI channel in the settings with the one on the device. |
| Recorder disabled | Needs firmware 2.5 or newer, and USB audio must be visible on the phone. |

**Reporting a problem**: send the text from the banner and the last lines of the device log (tap the display bar).

## 14. Good to know

- The community documentation of the device protocol does not fully confirm some functions: moving slots, preview, writing settings, project download and upload, live updates, switching project and group. In the first test on the device everything worked, but not every function has been checked in detail. If in doubt, try them on a sample you do not need first.
- The app changes nothing without asking. Deleting goes through the trash, replacing keeps the old version, restore overwrites only when ticked.
- The app has no subscription and needs no internet.
