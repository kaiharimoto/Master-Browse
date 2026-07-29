Kills the last swipe flash.

## Fixed
- **Stretched-frame flash on some videos** — the video surface is provisionally laid out until the decoder reports the file's true dimensions, and on some videos the first frame arrived before that report, briefly revealing it stretched to screen shape. The swipe poster now stays up until **both** the first frame has rendered **and** the true dimensions are in, and the provisional surface uses the poster's own aspect ratio — so a misshapen frame can never reach the screen.

## Install
Installs **in place** — `MENU → Update from GitHub` on v1.6+. Coming from v1.5 or earlier: uninstall once, then sideload.

versionName 1.9 / versionCode 10 — min SDK 30, target SDK 35.
