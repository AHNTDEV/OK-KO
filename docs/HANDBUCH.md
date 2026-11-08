# KO II Companion: Handbuch

Version 0.3.0. Die App steuert die Teenage Engineering EP-133 K.O. II über USB-C: Samples verwalten und bearbeiten, Pads belegen, Beats programmieren, Akkorde spielen, aufnehmen und sichern.

Eine englische Fassung steht in [MANUAL.md](MANUAL.md). Die Bezeichnungen der Tasten stehen in der App auf Englisch. Dieses Handbuch nennt sie in Anführungszeichen, damit du sie wiederfindest.

## 1. Los geht's

**Das brauchst du**
- Ein Android-Handy (ab Android 8) mit USB-Host-Funktion.
- Eine EP-133 K.O. II mit Firmware 2.0.5 oder neuer. USB-Audio (Recorder) gibt es ab Firmware 2.5.
- Ein USB-C-**Datenkabel**. Reine Ladekabel funktionieren nicht.

**Verbinden**
1. K.O. II einschalten und per Kabel mit dem Handy verbinden.
2. Beim ersten Mal fragt Android nach der USB-Berechtigung. Bestätige sie.
3. Oben in der Display-Leiste steht danach zum Beispiel „K.O. II FW 2.5.1“ und der freie Speicher. Steht dort „No device“, ist das Gerät nicht erkannt. Siehe Abschnitt 13.

Tippst du auf die Display-Leiste, öffnet sich die Geräteseite mit Seriennummer, Firmware, Modus, Speicher und dem Geräte-Log. Daneben liegt „Settings“.

**Aufbau der App**
Unten wechselst du zwischen sechs Tabs: Pads, Beats, Sounds, Keys, Rec, Library. Längere Vorgänge (Upload, Backup, Export) laufen im Hintergrund weiter, mit Fortschrittsbalken unten und einer Benachrichtigung. Meldungen und Fehler erscheinen in einem Banner über den Tabs. „OK“ schließt es.

## 2. Pads

Hier siehst du die Pads eines Projekts, so angeordnet wie am Gerät (10 11 12 oben, 1 2 3 unten).

- **Project 1 bis 9** und **Group A bis D** wählen, was angezeigt wird. Jedes Pad zeigt seine Nummer und den Namen seines Samples.
- **Play pads**: Pads antippen löst sie am K.O. II aus (MIDI-Note, Lautstärke aus dem Regler „Velocity“). Gedrückt halten hält die Note.
- **Assign**: Pad antippen und ein Sample zuordnen (siehe unten).
- **Transport**: „Play“, „Stop“ und „Cont“ senden MIDI Start, Stop und Continue. Mit „+“ und „-“ stellst du das Tempo ein, „Clock“ sendet MIDI-Clock mit diesem Tempo.
- **Show this project and group on the K.O. II**: Ist der Haken gesetzt, schaltet das Gerät auf das Projekt und die Gruppe um, die du in der App wählst. Diese Funktion ist vom Entwickler noch nicht ausführlich geprüft.

**Ein Sample einem Pad zuordnen**
1. „Assign“ wählen und das Pad antippen.
2. Oben „Device sounds“: Liste der Sounds auf dem Gerät mit Filter. „Play“ hört den Sound an, „Assign“ weist ihn zu.
3. Oben „Loops“: deine Loops vom Handy (siehe Abschnitt 8). „Play“ hört sie auf dem Handy an. „Put on pad“ wandelt den Loop um, lädt ihn in den nächsten freien Slot und legt ihn auf das Pad.

## 3. Sounds

Hier verwaltest du die bis zu 999 Slots des Geräts. Pro Seite siehst du 48 Slots. Mit „<“ und „>“ blätterst du.

- **Suche**: Feld oben, Suche nach Name oder Slot-Nummer.
- **Auswählen**: Slot antippen. Darunter erscheinen die Aktionen.
- **Mehrfachauswahl**: Einen Sound gedrückt halten, dann weitere antippen. Möglich sind „Delete“, „Export“ (als WAV in einen Ordner deiner Wahl) und „Clear“.

**Aktionen für einen Sound**
- **Play**: spielt ihn am Gerät als kurze Vorschau.
- **Move**: Nummer wählen, dann den Ziel-Slot antippen. Ist er belegt, tauschen beide Sounds die Plätze.
- **Edit**: öffnet die Detailseite (Abschnitt 4).
- **Save**: speichert den Sound als WAV-Datei.
- **Delete**: löscht den Sound auf dem Gerät. Eine Kopie bleibt im App-Papierkorb (Abschnitt 9).
- **Upload**: lädt Audiodateien hoch (Abschnitt 5).

## 4. Sound-Detail und Sample-Editor

**Detailseite** (Sounds → Edit)
- Wellenform des Sounds, dazu „Play“, „Rename“, „Save WAV“.
- Regler für Root note, Pitch, Pan, Volume, Attack, Release und BPM. „Save changes“ schreibt nur die Werte, die du geändert hast. „Reset“ verwirft die Änderungen.
- „Edit sample“ öffnet den Sample-Editor mit diesem Sound.

**Sample-Editor**
Öffnen kannst du ihn über „Edit sample“ (Sound vom Gerät) oder über „Editor“ oben auf dem Sounds-Tab, dort mit „Open a file from your phone“ (WAV, AIFF, MP3, FLAC, M4A, OGG).

- **Auswahl**: Auf der Wellenform ziehen, um einen Bereich zu markieren. Die Griffe an den Rändern lassen sich nachziehen, ein Tipp hebt die Auswahl auf. „Select all“ markiert alles.
- **Cut and shape**: „Crop“ behält nur die Auswahl, „Cut out“ entfernt sie, „Silence“ macht sie stumm, „Trim“ schneidet Stille an Anfang und Ende ab. „Reverse“, „Fade in“, „Fade out“ und „Mono“ sind selbsterklärend.
- **Level**: „Normalize“ hebt den Pegel auf Maximum, „Gain -3 dB“ und „Gain +3 dB“ ändern ihn (auf der Auswahl, wenn eine besteht).
- **Pitch in semitones**: -12, -1, +1, +12. Das Sample wird dabei wie bei einem Sampler schneller oder langsamer, also kürzer oder länger.
- **Play** spielt die Auswahl oder das ganze Sample auf dem Handy. **Undo** macht die letzten Schritte rückgängig (bis zu 20).
- **Save**
  - „Replace on device“ ersetzt das Original im selben Slot. Slot-Nummer und Einstellungen (Root note, Pitch und so weiter) bleiben erhalten. Die alte Version liegt danach im Papierkorb.
  - „Save as new“ legt das Ergebnis in den nächsten freien Slot.
  - „Export WAV“ speichert eine WAV-Datei auf dem Handy.
  - „Rename“ ändert den Namen.

Das K.O. II nimmt höchstens 40 Sekunden. Ist dein Sample länger, kürze es erst. Bis dahin sind „Replace on device“ und „Save as new“ gesperrt.

## 5. Dateien hochladen

1. Sounds → „Upload“, dann eine oder mehrere Audiodateien wählen.
2. Die Upload-Seite zeigt je Datei den Namen, die Dauer und ob sie „sent as is“ (schon im Gerätformat) oder „converted to 46875 Hz“ wird. Fehler stehen mit Grund dabei, zum Beispiel zu lang oder mehr als zwei Kanäle.
3. Lege bei „Start slot“ fest, ab welchem Slot belegt wird, und tippe „Send … file(s)“.

Die App wandelt alles in 16 Bit und 46875 Hz um. Mono und Stereo bleiben erhalten, mehr als zwei Kanäle gehen nicht. Namen werden auf das Gerät zugeschnitten: Endung weg, Kleinbuchstaben, höchstens 16 Zeichen.

## 6. Beats (Sequencer)

Ein Step-Sequencer, der die Pads des K.O. II vom Handy aus spielt.

- **Pattern 1 bis 8**: acht Speicherplätze. Änderungen werden automatisch gesichert.
- **Play** startet, „Stop“ beendet. Tempo mit „-“ und „+“, **Swing** mit dem Regler (verschiebt jeden zweiten Schritt).
- **8, 16, 24, 32 steps**: Länge des Patterns. Ein Schritt ist eine Sechzehntelnote.
- **Group A bis D**: welche zwölf Pads das Raster gerade zeigt. Alle vier Gruppen klingen gleichzeitig.
- **Raster**: Schritt einmal antippen = Schlag, zweimal = Akzent (lauter, mit Punkt), dreimal = löschen. Pad-Nummer links antippen = Pad anspielen, gedrückt halten = die Zeile leeren.
- **Also send MIDI start and clock to the K.O. II**: Aus, spielt das Handy die Pads allein. An, bekommt das Gerät zusätzlich Start und Tempo, damit sein eigener Sequencer folgt.
- **Clear** leert das Pattern, **Copy to next** kopiert es in den nächsten Platz.
- **Export MIDI** speichert das Pattern als MIDI-Datei, **Export file** als Pattern-Datei (JSON). **Import MIDI or pattern file** lädt beides in den gewählten Platz und ersetzt ihn. MIDI-Noten C2 bis B5 werden den Pads A bis D zugeordnet, andere Noten werden übersprungen.

Der Sequencer läuft auf dem Handy. Die eigenen Patterns des K.O. II kann die App nicht lesen oder ändern.

## 7. Keys (Akkorde und Arpeggiator)

Ein Keyboard über zwei Oktaven, das Noten an das K.O. II sendet.

- **Note, Maj, Min, Sus, Dim**: einzelne Note oder Dreiklang.
- **6, 7, 9**: fügt eine Erweiterung hinzu. **Low** ergänzt die tiefere Oktave.
- **Arp**: spielt den Akkord als Arpeggio. Wähle Modus (Up, Down, Up/Dn, Rnd), Rate von 1/4 bis 1/32 und 1 bis 3 Oktaven. Das Tempo kommt aus der BPM-Einstellung.
- Mit „-“ und „+“ neben dem Bereich verschiebst du die Oktave.

Die Noten gehen auf dem MIDI-Kanal aus den Einstellungen. Das K.O. II muss auf diesem Kanal hören.

## 8. Loop-Ordner

Unter Settings → „Loop folder (optional)“ wählst du einen Ordner auf dem Handy mit deinen Loops. Die App liest ihn mit der Systemauswahl und braucht dafür keine Speicherberechtigung. Unterordner bis drei Ebenen tief werden mitgelesen. „Rescan“ liest neu ein, „Change“ wechselt den Ordner, „Clear“ entfernt ihn.

Die Loops erscheinen beim Zuordnen (Pads → Assign → Loops), siehe Abschnitt 2.

## 9. Papierkorb

Gelöschte Sounds liegen im Papierkorb der App (Sounds → „Trash“, oder Library → „Trash“).

- **Restore** stellt einen Sound in seinen alten Slot zurück, oder in den ersten freien, wenn der Slot inzwischen belegt ist.
- **Remove** entfernt ihn endgültig, **Empty trash** leert alles.

Der Papierkorb liegt nur im Speicher der App. Löschst du die App-Daten oder die App, ist er weg.

## 10. Library: Projekte und Backup

**Projekte** (P01 bis P09): „Tar“ speichert ein Projekt als `.tar`-Archiv.

**Backup**
1. Haken setzen: „Sounds (as WAV)“ und/oder „Projects (as tar)“.
2. „Backup“ tippen und einen Speicherort wählen. Es entsteht eine Zip-Datei mit einer Übersicht (`meta.json`, ohne Seriennummer), den Sounds als WAV samt Einstellungen und den Projekten als Tar.
3. Ein Hinweis nennt übersprungene Elemente, zum Beispiel ein Projekt, dessen Archiv nicht gültig war.

**Restore**
1. „Restore“ tippen und die Zip-Datei wählen.
2. Die App zeigt, was drin ist, und lässt dich Sounds und Projekte einzeln abwählen.
3. Ohne den Haken „Replace what is already on the device“ werden belegte Slots und vorhandene Projekte übersprungen. Mit Haken werden sie überschrieben.
4. „Restore“ startet. Jede Datei wird mit ihrer Prüfsumme geprüft, beschädigte werden übersprungen.

Mach vor größeren Änderungen ein Backup.

## 11. Recorder

Nur mit Firmware 2.5 oder neuer.

- **Record K.O. II output**: nimmt die Stereo-Ausgabe des Geräts über USB auf. Du wählst die WAV-Datei als Ziel. Beim ersten Mal fragt Android nach der Mikrofon-Berechtigung, die auch für USB-Eingänge gilt. „Stop“ beendet die Aufnahme.
- **Bit depth**: 16, 24 oder 32 float.
- **Save last take as sample**: wandelt die Aufnahme um (bis 40 Sekunden) und legt sie im nächsten freien Slot ab.
- **Play from your phone into the K.O. II**: „Play file“ spielt eine Datei vom Handy in den USB-Eingang des Geräts. „Stop“ beendet sie. So samplst du Material vom Handy.

## 12. Settings

- **Theme**: „K.O. II“ (hell, orange) oder „Mono“ (dunkel, grau).
- **Loop folder**: siehe Abschnitt 8.
- **MIDI channel**: Kanal für Pads, Keys und Beats. Das K.O. II muss darauf hören.
- **Pad velocity**: Lautstärke der Pads und Keys.
- **Recording bit depth**: Standard für Aufnahmen.

## 13. Wenn etwas nicht klappt

| Problem | Was du tun kannst |
| --- | --- |
| „No device“ | Anderes Kabel probieren (Datenkabel!), Gerät neu verbinden, in der Geräteseite „Scan again“. |
| „Allow USB access“ | Berechtigung in der Android-Abfrage bestätigen. |
| „Error, tap for details“ | Auf die Leiste tippen. Dort steht der Grund, zum Beispiel zu alte Firmware oder kein K.O. II. |
| Zeitüberschreitung | Das Gerät war beschäftigt. Kurz warten und erneut versuchen, gegebenenfalls die Liste neu laden. |
| „No free slot“ | Alle 999 Slots sind belegt. Erst Sounds löschen. |
| Sound zu lang | Auf höchstens 40 Sekunden kürzen, am besten im Sample-Editor. |
| Nichts hörbar bei Pads, Keys oder Beats | MIDI-Kanal in den Einstellungen mit dem am Gerät vergleichen. |
| Recorder gesperrt | Firmware 2.5 oder neuer nötig, USB-Audio muss am Handy sichtbar sein. |

**Meldung an den Entwickler**: Schick den Text aus dem Banner und die letzten Zeilen des Geräte-Logs (Display-Leiste antippen).

## 14. Gut zu wissen

- Die Community-Dokumentation des Geräteprotokolls bestätigt einige Funktionen nicht abschließend: Slots verschieben, Vorschau, Schreiben von Einstellungen, Projekt-Download und -Upload, Live-Aktualisierung, Umschalten von Projekt und Gruppe. Im ersten Test am Gerät lief alles, im Detail geprüft ist aber nicht jede. Probiere sie bei Bedarf zuerst mit einem Sample, das du nicht brauchst.
- Die App verändert nichts ungefragt. Löschen geht über den Papierkorb, Ersetzen sichert die alte Version, Restore überschreibt nur mit Haken.
- Die App hat kein Abo und braucht kein Internet.
