# Master Browse

A utilitarian, tile-based folder-hierarchy media browser for Android tablets (built for the Samsung Galaxy Tab S11 Ultra). Dark, minimal, no animations.

## Features

- **Tile grid browser** — folders first, then images/videos, with fast thumbnails (Coil, sized to the tile, memory-cached; video tiles show a frame from 1s in).
- **Sees everything** — uses direct filesystem access ("All files access"), so hidden folders, dotfiles, and folders containing `.nomedia` all show up. MediaStore is never used.
- **Home folder** — `MENU → Set this folder as home` (or long-press a folder tile). The app opens there on launch; `HOME` jumps back.
- **Custom folder thumbnails** — long-press a folder tile → *Pick thumbnail for this folder*, then tap any media inside it. Or long-press a media tile → *Set as thumbnail for this folder*. Falls back to the first media file found inside (one subfolder level deep).
- **Fullscreen viewer** — tap a tile. Videos autoplay, loop forever, and have a seekbar + play/pause. Tap the screen to show/hide the controls.
- **Swipe navigation** — swipe left/right or up/down (when not zoomed in) to go to the next/previous item in the folder.
- **Zoom** — pinch to zoom images and videos, drag to pan when zoomed. The `1:1` / `FIT` button (or double-tap) toggles between fit-to-screen and pixel-perfect (1 media pixel = 1 screen pixel).
- **Split view** — the `SPLIT` button moves the current item to the left half and opens a tile picker on the right half to choose a second item. Each pane has independent zoom, seek, and swipe navigation. `PICK` re-opens the picker for that pane; `SINGLE` (or back) returns to one pane.

## Building

Open the project in Android Studio (or run `./gradlew assembleDebug` with an Android SDK installed; the wrapper uses Gradle 8.9 / AGP 8.5.2 / Kotlin 2.0), then install `app/build/outputs/apk/debug/app-debug.apk` on the tablet.

On first launch the app asks for **All files access** and sends you to the system settings toggle — this is what lets it browse hidden and `.nomedia` folders.

## Notes

- Back button: exits split view → exits the viewer → walks up the folder tree → exits the app at the home folder.
- Video thumbnails are cached in memory only, so the very first scroll through a video-heavy folder is slower than later visits.
- In the viewer, images are decoded at full resolution so pixel-perfect mode is truly 1:1.
