Stable signing starts here — in-app updates work from now on.

## Changed
- **Permanent signing key** — this and every future release are signed with the app's stable keystore (stored as a repo secret and used by CI). From this version onward, `MENU → Update from GitHub` installs updates **in place**: no more uninstalling, and your settings (home folder, pins, tile size, thumbnails) survive updates.

No app code changes since v1.5 (seamless swipe hand-off, hold-to-scrub 3×, filter, pins, rename/move/copy, fast scroll, auto-hide controls, help — see the v1.4/v1.5 notes).

## Install — one last time by hand
⚠️ v1.5 and earlier were signed with throwaway keys, so this build won't install over them. **Uninstall the old version once**, sideload `master-browse-v1.6.apk` from below — and that's the last time. All future releases install via `MENU → Update from GitHub`.

versionName 1.6 / versionCode 7 — min SDK 30, target SDK 35.
