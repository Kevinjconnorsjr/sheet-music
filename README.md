# ScoreStand

An Android sheet-music reader and editor: import scores, read them on stage, mark them up,
and practise along with attached audio tracks.

## Status

| Stage | Features | Status |
|---|---|---|
| 1. Library & reader | Import PDF/images, library grid, search, tags, sorting, setlists, reader with 4 layouts, Bluetooth pedals | ✅ Done |
| 2. Annotation | Pen, highlighter, eraser, text, music-symbol stamps, layers, undo/redo | ✅ Done |
| 3. Audio | Attach tracks, speed (keeps pitch), pitch (keeps speed), pan, volume, A–B loop, markers | ✅ Done |
| 4. Extras | Page links for repeats/D.S./coda, metronome, tuner, backup/export | ✅ Done |

## What works now

**Library**
- Import PDFs and images (photos or scans of sheet music) with the **Import** button, or
  by opening/sharing a PDF to ScoreStand from another app
- **Recordings come along with the music**: in Import, pick the sheet music *and* its audio
  files together and the recordings are saved with that score (with several scores, you're
  asked which one they belong to). Importing just a score offers **Add audio tracks** right
  after; any score's long-press menu has **Add audio tracks…** too
- Grid of scores with thumbnails, title, composer and page count
- Search (title, composer, tags), tag filter chips, and sorting by title, composer,
  recently opened or recently added
- Long-press a score to edit details, add it to a setlist, or delete it
- **Long names** (library ⋮): *Wrap* shows every name in full (default); *Shorten in the middle*
  keeps names on one line as `Everybody Loves So…Tenor predominant`, so the start and the end
  both show. Applies to scores, setlists, audio tracks and markers
- **Sharing from Google Drive**: Android's file picker doesn't show Drive folder shortcuts, but
  the Drive app does — select the PDF and its recordings there, then *Share → Send a copy →
  ScoreStand*. They're saved as one score with its tracks; recordings shared on their own ask
  which score they belong to

**Setlists**
- Create, rename and delete setlists; add scores; reorder; remove
- **Perform** a setlist (▶): turning past the last page of one piece opens the next piece

**Reader**
- Four page layouts, chosen from the toolbar (remembered per score):
  - **Single page** — swipe or tap to turn
  - **Two pages side by side** — good for tablets in landscape
  - **Half-page turns** — the top half changes to the next page first, so you can read ahead
  - **Continuous vertical scroll** — all pages in one strip, with **auto-scroll**
    (adjustable speed) and an adjustable page width. Touching the screen pauses
    auto-scroll briefly.
- **Pinch with two fingers to zoom** (up to 400%) in any layout; zoomed pages re-render sharply.
  Two-finger drags pan, scroll the music (continuous mode) or turn the page (paged layouts).
  While zoomed, one finger pans; tap the % chip to return to the whole page
- **Trim margins** (layout menu): crops the white space around each page so the music fills
  the screen width
- Tap the left third of the screen to go back, the right third to go forward, and the middle to
  show or hide the toolbars (hiding them goes full-screen)
- Bluetooth page-turn pedals and keyboards: Page Down / → / ↓ / Space / Enter = forward;
  Page Up / ← / ↑ = back
- Page slider for jumping, remembers the last page, keeps the screen awake

**Marking up** (tap ✏️ in the reader)
- Tools: **hand** (scroll/turn pages as normal), **pen**, **highlighter**, **eraser**,
  **text** notes and **musical symbols**
- Six ink colours, five highlighter colours, three sizes per tool
- Symbols: dynamics (pp–ff, sfz), crescendo/diminuendo hairpins, cresc./dim./rit./a tempo,
  fermata, accent, breath mark, circle, up-/down-bow, "watch out" glasses, ♯ ♭ ♮, fingerings 1–5
- **Layers**: keep separate sets of markings (e.g. "My markings", "Conductor", "Lesson"),
  show/hide each one, choose which one new marks go on
- Undo/redo, clear page, ◀ ▶ page buttons while drawing (pedals work too)
- One finger draws; **two fingers always move, zoom or shrink the music** (and never leave a mark)
- Marks are stored separately from the PDF (the original file is never changed) and scale with
  the page in every layout. Half-page mode shows whole pages while you're marking up.

**MusicXML scores: transpose and play back**
- Import `.musicxml`, `.mxl` or MusicXML `.xml` files (from MuseScore, Finale, Sibelius, Dorico,
  or a PDF converted with a music-recognition tool such as Audiveris). They're shown as live,
  engraved notation — pinch to zoom
- **Transpose** to any key (±12 semitones): notes and key signatures change; saved per score
- **Play it** as MIDI with a cursor that follows the music; start from any bar; tempo 25–200%;
  hear all parts, mute parts or play one part alone; use the score's instruments or piano,
  choir, strings, organ, flute or "oohs"
- **Share MIDI file** (score menu): exports the music, in the chosen key, as a `.mid`
- Repeats are played straight through. Markings and page tools apply to PDF scores only

**Audio tracks** (tap 🎧 in the reader)
- Attach any number of recordings (MP3, M4A/AAC, WAV, FLAC, OGG…) to a score — several at once
  from the file picker; switch,
  rename or remove them from the track menu
- Waveform you can tap or drag to seek; ±5 s skip; play/pause from headphone or Bluetooth
  media buttons
- **Speed** 25–200% without changing pitch (±5% steps)
- **Pitch** in semitones (♭/♯) plus ±50 cent fine-tuning, without changing speed
- **Pan** left/right (works on mono recordings too) and **volume**
- **A–B repeat**: tap *Set A* and *Set B* while listening; toggle or clear the loop
- **Page turns synced to the recording**: tap **Turn page here** at the moment the page should
  turn, or switch on **Record turns**, press play and turn pages as you normally would — each turn
  is saved. With **Turn pages** on, playback turns the pages for you from then on
- **Edit turns**: a list of every recorded turn with its exact time (to a tenth of a second);
  nudge each one earlier/later, set it to the moment now playing, change the page it turns to,
  play from just before it to check, or remove it. **Shift all** moves every turn at once
- **Markers**: drop a marker at the current moment; it's linked to the page you're on.
  With **Turn pages** on, the score jumps to that page when playback reaches the marker
  (long-press a marker to rename, re-link or delete it)
- Every track remembers its own speed, pitch, pan, volume and loop
- The panel stays up when the toolbars are hidden, so you can play along full-screen;
  playback pauses if headphones are unplugged or a call comes in

**Jump links** (link tool ↪ in the markup toolbar)
- Tap where a jump starts (a repeat sign, D.S., D.C., "to Coda"…), then go to where it lands
  and tap there; pick a label (Repeat, D.S., D.C., To Coda, Coda, Fine, 1st/2nd ending, or your own)
- While reading, tap the badge to jump — in continuous scroll it scrolls right to the spot.
  A **Back to page N** chip takes you back
- Tap a badge with the link tool to remove it

**Metronome** (⋮ → Metronome in the reader)
- 30–300 BPM with slider, ±1 buttons, presets and **tap tempo**; tempo names (Adagio, Allegro…)
- 1–12 beats per bar with an accented first beat and beat lights
- Sample-accurate clicks that don't drift; tempo and beats are remembered per piece

**Keyboard** (⋮ → Keyboard in the reader)
- Piano keyboard sized to your screen; play chords with several fingers or slide for a glissando
- Octave arrows, sustain pedal, and optional note names on every key

**Tuner** (⋮ → Tuner in the reader or the library)
- Chromatic tuner with note name, octave, frequency and a ±50 cent meter (green when within 5¢)
- Adjustable reference pitch (A4 = 400–480 Hz, e.g. 442 or 415 for baroque) and sharps/flats
- Listens live through the microphone; nothing is recorded or saved

**Sharing & backup**
- **Share as PDF** (score long-press menu, or ⋮ in the reader): a new PDF with your visible
  markings drawn in — for printing, emailing or sending to bandmates
- **Back up library…** (library ⋮): one .zip with every score, marking, setlist, audio track
  and setting. **Restore from backup…** replaces the library with a backup (handy for moving
  to a new phone) and restarts the app

## Building

Requirements: JDK 17+ and the Android SDK (platform 35).

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### Installing on your phone

Every change merged to `main` is published as a **GitHub Release** with a ready-to-install APK
(`ScoreStand-0.1.N.apk`). Published builds are all signed with the same key, so each new
version installs **over** the previous one and keeps your library.

**Easiest: let Obtainium install and update it for you**
1. Install [Obtainium](https://github.com/ImranR98/Obtainium) (free, open source).
2. Tap *Add App*, paste `https://github.com/Kevinjconnorsjr/sheet-music`, tap *Add*, then *Install*.
3. From then on Obtainium tells you when a new version is out — tap **Update**.

**Manual:** open the [latest release](https://github.com/Kevinjconnorsjr/sheet-music/releases/latest)
on your phone, download `ScoreStand-0.1.N.apk`, open it and tap *Install/Update* (allow installs
from your browser the first time).

Branch and pull-request builds are also on each Actions run as the **ScoreStand-apk** artifact
(these may use a throw-away key if the signing secret isn't available to them).

### Signing key (maintainers)

The key is never committed. CI reads it from the repository secret `SIGNING_KEYSTORE_BASE64`
(the keystore file, base64-encoded; store and key password `scorestand`, alias `scorestand`).
Without the secret, builds still work but use a throw-away key and no release is published.
Keep a copy of the keystore somewhere safe: if it's lost, the next version can't install over
the old one (you'd back up, uninstall, reinstall and restore).

> Builds made before the fixed key (the first Actions runs) used a throw-away key. If one of
> those is installed, back up your library (library ⋮ → Back up), uninstall it once, install
> the new version, then restore.

## Tech

Kotlin · Jetpack Compose (Material 3) · Room · Navigation Compose · Android `PdfRenderer` ·
[OpenSheetMusicDisplay](https://opensheetmusicdisplay.org) (BSD-3-Clause, bundled in
`app/src/main/assets/notation`) for notation · Android's built-in General MIDI synth ·
Media3 ExoPlayer (speed/pitch via its Sonic processor, plus a custom pan processor).
Min SDK 26 (Android 8.0), target SDK 35. All data stays on the device: score files live in
the app's private storage and the original files are never modified.
