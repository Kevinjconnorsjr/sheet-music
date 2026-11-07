# ScoreStand

An Android sheet-music reader and editor: import scores, read them on stage, mark them up,
and practise along with attached audio tracks.

## Status

| Stage | Features | Status |
|---|---|---|
| 1. Library & reader | Import PDF/images, library grid, search, tags, sorting, setlists, reader with 4 layouts, Bluetooth pedals | ✅ Done |
| 2. Annotation | Pen, highlighter, eraser, text, music-symbol stamps, layers, undo/redo | ⏳ Next |
| 3. Audio | Attach tracks, speed (keeps pitch), pitch (keeps speed), pan, volume, A–B loop, markers | ⏳ Planned |
| 4. Extras | Page links for repeats/D.S./coda, metronome, tuner, backup/export | ⏳ Planned |

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

Kotlin · Jetpack Compose (Material 3) · Room · Navigation Compose · Android `PdfRenderer`.
Min SDK 26 (Android 8.0), target SDK 35. All data stays on the device: score files live in
the app's private storage and the original files are never modified.
