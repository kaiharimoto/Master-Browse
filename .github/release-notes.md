A mini Master Browse that floats on top of other apps.

## Added
- **Pop out** — `POP OUT` in the viewer shrinks the whole app into a small window drawn on top of whatever you switch to, and drops the fullscreen app to the background. It is the *complete* app, not a video surface: browse folders, open media, swipe between items, pinch to zoom, scrub video and use the keyboard, all inside the floating window. `EXPAND` brings the fullscreen app back showing whatever the mini window was on; `✕` closes it.
- **Drag and resize** — drag the window by its title bar, resize it from the corner grip. Position and size are remembered, and the mini window keeps its own tile size so pinching tiles small in it never shrinks the fullscreen grid.
- **Fits the media** — the window takes the aspect ratio of whatever it is showing, so a portrait photo gets a tall window and a 16:9 video a wide one, instead of letterbox bars. It reshapes as you swipe, keeping the footprint you last dragged it to.

## Notes
- Needs the **Display over other apps** permission; the app asks the first time you tap `POP OUT`. A low-priority ongoing notification keeps the window alive while it is up, with `EXPAND` and `CLOSE` actions.
- Back inside the mini window walks the same chain as fullscreen (viewer → grid → up the tree), and closes the window at the home folder. Tap the window first — an overlay only receives keys once it has focus, so the app underneath keeps its keyboard otherwise.
- Rename, `FILTER`, Help and the updater are left out of the mini window: the first two need the on-screen keyboard, which the overlay deliberately leaves to the app underneath, and the last two open their own window, which an overlay cannot host.

## Install
Installs **in place** — `MENU → Update from GitHub` on v1.6+. Coming from v1.5 or earlier: uninstall once, then sideload.

versionName 1.10 / versionCode 11 — min SDK 30, target SDK 35.
