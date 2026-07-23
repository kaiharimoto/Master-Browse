Instant thumbnails and six usability features.

## Performance
- **Video thumbnails load once and stay loaded** — each video's thumbnail is now extracted a single time into a persistent on-disk cache (and the whole folder pre-generates in the background when you open it). Scrolling a video-heavy folder is instant after the first visit, including across app restarts. The cache self-prunes past 2 GB, oldest first.

## New
- **Filename filter** — the `FILTER` toolbar button narrows the current folder's tiles as you type; opening media swipes through just the matches.
- **Pinned folders** — long-press a folder → *Pin folder* (or `MENU` for the current one); the `★` button jumps to any pin.
- **Rename / move / copy** — in every long-press menu. Move/copy opens a folder picker; name collisions get " (1)"-style suffixes, and internal ↔ SD moves are safe (copy-then-delete).
- **Fast scroll + scroll memory** — drag the right-edge bar in long folders; every folder reopens at the spot you left it.
- **Auto-hiding viewer controls** — the overlay disappears 3 s after your last touch/key (never mid-scrub); tap to bring it back.
- **Help** — `MENU → Help` lists every gesture and key.

## Install
⚠️ **This build is signed with a new key**, so it will **not** install over v1.2/v1.3 — including via `MENU → Update from GitHub`. **Uninstall the old version once**, then sideload `master-browse-v1.4.apk` from below. Until a stable keystore is stored as the `RELEASE_KEYSTORE_B64` repo secret, each CI-built release is signed with a fresh key and needs this same uninstall step.

versionName 1.4 / versionCode 5 — min SDK 30, target SDK 35.
