Viewer fixes and batch file management.

## Fixed
- **Swiping on videos works again** — v1.5's hold-to-scrub could hijack slower swipes into a 3× hold. The hold now only arms if your finger stays genuinely still for the long-press delay; any movement cancels straight into normal paging.
- **No more thumbnail flash or jitter when swiping** — the current, next, and previous items are pre-decoded into ready-to-draw previews, so swipes and the hand-off to the full photo/video render on the very first frame with no async gap.

## New
- **Multi-select** — long-press → *Select*, then tap tiles (files or folders) to build a selection; a bar shows the count with **MOVE / COPY / DELETE / CANCEL**.
- **Move anywhere** — in the destination picker, the back gesture now walks *up* the folder tree (alongside the `UP` button), so moving items to a parent or a different volume is easy.
- **Filename toggle** — `MENU → Hide filenames` for a clean grid; remembered across launches.
- **Much bigger tiles** — pinch now scales tiles up to roughly full-screen width (1200dp, up from 400).
- **Deeper folder thumbnails** — the automatic folder-tile thumbnail now searches up to four levels deep instead of one, so nested libraries stop showing blank folders.

## Install
This update **installs in place** — use `MENU → Update from GitHub` on v1.6, or sideload over it. No uninstall needed (signing has been stable since v1.6). Coming from v1.5 or earlier: uninstall once, then sideload.

versionName 1.7 / versionCode 8 — min SDK 30, target SDK 35.
