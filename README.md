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
- Grid of scores with thumbnails, title, composer and page count
- Search (title, composer, tags), tag filter chips, and sorting by title, composer,
  recently opened or recently added
- Long-press a score to edit details, add it to a setlist, or delete it

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
- A second finger cancels the current stroke, so resting your palm or pinching doesn't leave marks
- Marks are stored separately from the PDF (the original file is never changed) and scale with
  the page in every layout. Half-page mode shows whole pages while you're marking up.

**Audio tracks** (tap 🎧 in the reader)
- Attach any number of recordings (MP3, M4A/AAC, WAV, FLAC, OGG…) to a score; switch,
  rename or remove them from the track menu
- Waveform you can tap or drag to seek; ±5 s skip; play/pause from headphone or Bluetooth
  media buttons
- **Speed** 25–200% without changing pitch (±5% steps)
- **Pitch** in semitones (♭/♯) plus ±50 cent fine-tuning, without changing speed
- **Pan** left/right (works on mono recordings too) and **volume**
- **A–B repeat**: tap *Set A* and *Set B* while listening; toggle or clear the loop
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
Media3 ExoPlayer (speed/pitch via its Sonic processor, plus a custom pan processor).
Min SDK 26 (Android 8.0), target SDK 35. All data stays on the device: score files live in
the app's private storage and the original files are never modified.
