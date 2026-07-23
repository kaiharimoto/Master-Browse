# Master Browse

A utilitarian, tile-based folder-hierarchy media browser for Android tablets (built for the Samsung Galaxy Tab S11 Ultra). Dark, minimal, no animations.

## Features

- **Tile grid browser** — folders first, then images/videos, with fast thumbnails (Coil, sized to the tile, memory-cached; video tiles show the exact frame 25% into the video, so short clips and fade-from-black intros still get a real picture).
- **Sees everything** — uses direct filesystem access ("All files access"), so hidden folders, dotfiles, and folders containing `.nomedia` all show up. MediaStore is never used.
- **Home folder** — `MENU → Set this folder as home` (or long-press a folder tile). The app opens there on launch; `HOME` jumps back.
- **Custom folder thumbnails** — long-press a folder tile → *Pick thumbnail for this folder*, then tap any media inside it. Or long-press a media tile → *Set as thumbnail for this folder*. Falls back to the first media file found inside (one subfolder level deep).
- **Fullscreen viewer** — tap a tile. Videos autoplay, loop forever, and have a live-scrubbing seekbar + play/pause. Tap the screen to show/hide the controls.
- **Swipe navigation** — swipe left/right (when not zoomed in) to go to the next/previous item; the item follows your finger and snaps like a pager. Double-tap the left/right half of the screen to jump previous/next instantly.
- **Keyboard** — with a hardware keyboard, `←`/`→` go to the previous/next item, and (on videos) hold `J` to rewind or `K` to fast-forward, accelerating the longer you hold.
- **Zoom** — pinch to zoom images and videos, drag to pan when zoomed. The `1:1` / `FIT` button toggles between fit-to-screen and pixel-perfect (1 media pixel = 1 screen pixel).
- **Split view** — the `SPLIT` button moves the current item to the left half and opens a tile picker on the right half to choose a second item. Each pane has independent zoom, seek, and swipe navigation. `PICK` re-opens the picker for that pane; `SINGLE` (or back) returns to one pane.

- **Storage toggle** — the `SD`/`INT` button next to `MENU` jumps between internal storage and the SD card.
- **Pinch tile size** — pinch anywhere on the tile grid to grow/shrink the tiles; the size is remembered.
- **Trash** — long-press any folder or media tile → *Delete* (with confirmation). Items move to a `.MasterBrowseTrash` folder at the root of the same volume, named `<epochMillis>_<name>`, and are permanently purged 30 days later (checked on app launch). Browse the trash folder like any other to recover things.
- **In-app updates** — `MENU → Update from GitHub` fetches the latest GitHub release of this repo and downloads/installs its APK asset. No automatic checks.

## Building

Open the project in Android Studio (or run `./gradlew assembleDebug` with an Android SDK installed; the wrapper uses Gradle 8.9 / AGP 8.5.2 / Kotlin 2.0), then install `app/build/outputs/apk/debug/app-debug.apk` on the tablet.

On first launch the app asks for **All files access** and sends you to the system settings toggle — this is what lets it browse hidden and `.nomedia` folders.

### Signing (one-time setup)

The first build auto-generates `app/release.keystore` and signs **both debug and release** builds with it. **Commit that file** after your first build — then every APK, whether built locally or by CI, has the same signature and in-app updates install cleanly. If signatures ever mismatch (e.g. an old debug install), uninstall the app once and install the new APK.

### Releases (for in-app update)

Pushing a tag like `v1.2` triggers the GitHub Actions workflow, which builds a release APK (versionCode = CI run number, versionName = tag) and attaches it to a GitHub release. `MENU → Update from GitHub` then finds it. The release must be visible without authentication (public repo, or make releases public).

## Notes

- Back button: exits split view → exits the viewer → walks up the folder tree → exits the app at the home folder.
- Video thumbnails are cached in memory only, so the very first scroll through a video-heavy folder is slower than later visits.
- In the viewer, images are decoded at full resolution so pixel-perfect mode is truly 1:1.
