Smoother viewer: seamless swipes and hold-to-scrub.

## Improved
- **No more flash when swiping** — committing a swipe (or opening an item) used to flash black/"Loading…" between the sliding thumbnail and the real media. The thumbnail now stays on screen until the content can actually draw — the first rendered frame for videos, the finished full-resolution decode for photos — then swaps in place. Photos just appear to sharpen; videos go thumbnail → playback with no black gap.

## New
- **Hold to scrub** — press and hold the **right half** of a video to fast-forward at **3× with audio** (real playback, pitch-corrected) until you let go; playback then returns to normal speed and your previous play/pause state. Hold the **left half** to rewind at 3× (video only — Android decoders can't run backward, so rewind has no audio). Small movements cancel into the usual gestures; taps, double-taps, swipes, pinch, and the seek bar all behave as before. Listed in `MENU → Help`.

## Install
⚠️ **This build is signed with a new key**, so it will **not** install over earlier versions — including via `MENU → Update from GitHub`. **Uninstall the old version once**, then sideload `master-browse-v1.5.apk` from below. Until a stable keystore is stored as the `RELEASE_KEYSTORE_B64` repo secret, each CI-built release is signed with a fresh key and needs this same uninstall step.

versionName 1.5 / versionCode 6 — min SDK 30, target SDK 35.
