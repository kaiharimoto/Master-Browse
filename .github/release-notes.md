Sort photos and video stills into training-set folders without touching the originals — and a cleaner, more modern look.

## Added
- **Dataset mode** — pick a dataset folder once (`⋮ → Set dataset folder…`, or `Dataset` in the viewer); each subfolder is a category. With `Dataset` on, the viewer shows a fixed bar of category buttons under the media: tap one, or press `1`–`9`, to **copy** the item in. The button lights up, an item can be in several categories, and tapping a lit button moves just that copy to the trash. Originals are only ever read. `New` creates a category and adds the current item to it; `Folder` switches dataset folders.
- **Video stills** — on a video, a category button saves the *frame on screen* as a full-resolution PNG named after its timestamp (`clip_01m02s345.png`) — the exact frame, not the nearest keyframe. The button counts the stills that category holds from the video and lights when paused on one, so a second tap undoes it.
- **Frames picker** — `Frames` on a video opens a grid of 12 / 24 / 48 / 96 evenly spaced stills. Tap the good ones (or `All`), then a category, to save them in one go; stills already saved are marked with their categories.
- **Batch send** — in the grid, *Select* some tiles and use `Dataset` to copy them all into a category.
- **Refreshed look** — icon toolbars, rounded pill buttons and tiles, one accent colour, gradient name labels, a folder name with its path under it, and viewer controls that float over the media on translucent glass and fade in and out. In the floating window, buttons shrink to icons.
- **Split in the floating window** — `Split` works in the mini window too; it widens to hold both panes.

## Fixed
- **True-aspect rows** — the last tile of each row was cut off slightly by the grid's side padding.

## Install
Installs **in place** — `⋮ → Update from GitHub` (labelled `MENU` before this version) on v1.6+. Coming from v1.5 or earlier: uninstall once, then sideload.

versionName 1.11 / versionCode 12 — min SDK 30, target SDK 35.
