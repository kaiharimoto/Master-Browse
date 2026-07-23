Video playback fixes.

## Fixes
- **Seekbar scrubbing** — playback pauses while you drag, seeks are frame-accurate instead of keyframe-only, and a seek pump always jumps to the newest finger position. The preview shows far more frames and no longer stutters or lags behind a backlog of stale seeks.
- **Background playback** — video (and its audio) now pauses when you switch apps, turn the screen off, or lose focus, and resumes on return only if it was playing before.
- **Black video thumbnails** — tiles now grab the exact frame 25% into the video instead of the nearest keyframe to 1s, which rendered black for short clips and fade-from-black intros.
- **J/K jog** — hold-to-rewind/fast-forward now advances an independent target position with exact seeks, so it no longer gets stuck looping the same snippet inside a long GOP.

## Install
⚠️ **This build is signed with a new key** (the previous signing keystore was not preserved), so it will **not** install over v1.2 — including via `MENU → Update from GitHub`. **Uninstall the old version once**, then sideload `master-browse-v1.3.apk` from below. Until a stable keystore is stored as the `RELEASE_KEYSTORE_B64` repo secret, each CI-built release is signed with a fresh key and needs this same uninstall step.

versionName 1.3 / versionCode 4 — min SDK 30, target SDK 35.
