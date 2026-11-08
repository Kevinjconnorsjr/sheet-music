# ScoreStand

An Android sheet-music reader and editor: import scores, read them on stage, mark them up,
and practise along with attached audio tracks.

## Status

| Stage | Features | Status |
|---|---|---|
| 1. Library & reader | Import PDF/images, library grid, search, tags, sorting, setlists, reader with 4 layouts, Bluetooth pedals | ✅ Done |
| 2. Annotation | Pen, highlighter, eraser, text, music-symbol stamps, layers, undo/redo | ✅ Done |
| 3. Audio | Attach tracks, speed (keeps pitch), pitch (keeps speed), pan, volume, A–B loop, markers | ✅ Done |
| 4. Extras | Page links for repeats/D.S./coda, metronome, tuner, backup/export | ⏳ Next |

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

## Building

Requirements: JDK 17+ and the Android SDK (platform 35).

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Every push also builds the APK on GitHub Actions. Open the **Actions** tab, pick the latest
**Build APK** run, and download **ScoreStand-debug-apk** to install on a device
(enable "Install unknown apps" for your browser or file manager).

## Tech

Kotlin · Jetpack Compose (Material 3) · Room · Navigation Compose · Android `PdfRenderer` ·
Media3 ExoPlayer (speed/pitch via its Sonic processor, plus a custom pan processor).
Min SDK 26 (Android 8.0), target SDK 35. All data stays on the device: score files live in
the app's private storage and the original files are never modified.
