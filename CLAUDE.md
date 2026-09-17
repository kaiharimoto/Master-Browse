# Master Browse

Utilitarian, tile-based folder-hierarchy media browser for Android tablets (built for the
Samsung Galaxy Tab S11 Ultra). Kotlin + Jetpack Compose, one `:app` module, dark and
minimal, no animations. Personal app, sideloaded from GitHub releases.

## Branching — read this first

**All work happens on `main`. Do not create per-session or per-feature branches.**

Start every session with `git fetch origin && git status` and make sure you are on `main`
at `origin/main`. Commit and push there.

Claude Code on the web hands each session a branch name of its own in the session config.
That is the harness talking, not this repo's convention. If you are given one, land the
work on `main` before you finish — fast-forward or rebase onto `origin/main` and push
there. Never leave a change living only on a side branch: a session in July 2026 built a
whole feature on a branch cut from a *stale* default branch and nearly shipped a release
that reverted seven versions of work.

If `main` has moved under you, rebase onto it rather than merging, and re-run the checks
below before pushing.

## Signing — do not break this

`app/release.keystore` is **committed on purpose** (added at v1.6). It is what makes every
APK carry the same signature, which is what makes `MENU → Update from GitHub` install in
place instead of failing with a signature mismatch.

- Never add it to `.gitignore`, never delete it, never let the build regenerate it. The
  Gradle script auto-generates a fresh random keystore if the file is missing, which
  silently produces APKs that cannot install over an existing install.
- Before publishing, confirm the signature matches the previous release:
  `apksigner verify --print-certs <apk> | grep "SHA-256 digest"` — it must equal the
  digest of the previous release's APK.
- The certificate as of v1.10 is
  `03cb4a8ed510a6533921524583d0a83c692b39919aaf2c4dc75bc84d6dfc3619`.

## Cutting a release

Releases go out through `.github/workflows/release.yml` on manual dispatch. Do not build
and upload an APK by hand.

1. Rewrite `.github/release-notes.md` for this version (the workflow uses it verbatim as
   the release body). Follow the house style: a one-line summary, then `## Added` /
   `## Fixed` with bold lead-ins, then `## Install`, then a
   `versionName X.Y / versionCode N — min SDK 30, target SDK 35` line.
2. Commit as `Write vX.Y release notes` and push to `main`.
3. Dispatch the workflow with `tag` (e.g. `v1.10`) and `versionCode`. **`versionCode` must
   increase every release** — Android refuses to install a lower one. v1.10 was 11.
4. The workflow builds, signs, creates the release and attaches
   `master-browse-<tag>.apk`. Verify the published asset's signature per above.

`versionCode`/`versionName` default to `2`/`1.1` when the properties are absent, so a
local `assembleRelease` without `-PversionCode=` produces an unreleasable APK. That is
fine for testing, not for shipping.

## Verifying changes

No emulator or device here, so everything is static. Run all three before pushing:

```
./gradlew assembleDebug testDebugUnitTest lintDebug
```

`lintDebug` is expected to pass with **zero errors** — keep it that way. If the Android
SDK is missing (`ANDROID_HOME` unset), install command-line tools, then
`sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"` and write
`sdk.dir` into `local.properties`. Maven Central rate-limits parallel fetches through the
proxy; `--max-workers=1` and a retry get past the 429s.

Tests are plain JVM JUnit over pure-Kotlin logic (`PackRowsTest`, `FileRepoTest`,
`OverlayGeometryTest`). Keep new logic that *can* be Android-free Android-free so it can
be tested — `OverlayGeometry` is the model for this.

## Architecture

- `MainActivity` → `AppRoot` switches between `BrowserScreen` (tile grid) and
  `ViewerScreen` (one or two `MediaPane`s).
- `MediaPane` is the core: image via Coil, video via ExoPlayer into a `TextureView`, with
  zoom/pan (`ZoomState`), swipe paging, a swipe poster, and keyboard nav (←/→, hold J/K).
- `FileRepo` uses direct filesystem access (`MANAGE_EXTERNAL_STORAGE`), never MediaStore —
  that is what makes hidden folders, dotfiles and `.nomedia` folders visible.
- `ThumbCache` caches video thumbnails on disk. `Prefs` is a plain SharedPreferences
  object.

### Pop-out mode (`overlay/`)

`OverlayService` hosts **the same `AppRoot`** in a `TYPE_APPLICATION_OVERLAY` window, so
the app runs twice from one Compose tree. `LocalAppHost.floating` says which host you are
in. Things that will break it if you forget:

- **No `Dialog`, `AlertDialog`, `DropdownMenu` or `Popup` may be reachable when floating.**
  They add a child window using a token that is not an activity token, so they throw
  `BadTokenException`. Menus and confirmations route through `LocalMenuController`
  (`ui/MenuHost.kt`), which draws them inline in the overlay's own window. If you add a
  menu or a confirm dialog, wire it through `MenuButton`/`MenuController` too.
- **Nothing needing the IME works when floating** (the overlay leaves the keyboard to the
  app underneath by design). Rename and FILTER are hidden there; hide new text input too.
- `FLAG_HARDWARE_ACCELERATED` on the window is mandatory or video renders black.
- Window drag/resize read raw screen coordinates from a plain `View`; Compose's
  window-local deltas stall once the window itself moves. Keep them in the shell chrome so
  they never fight `MediaPane`'s paging or `BrowserGrid`'s pinch.
- Tear down in this order: remove the view from the `WindowManager` *first* (that disposes
  the composition and releases the ExoPlayer), then destroy the lifecycle host.

## Conventions

- Match the surrounding style: terse utilitarian UI, ALL-CAPS `PaneButton` labels, colours
  as inline `Color(0xFF…)` literals, no animations.
- Comments explain *why*, especially where Android forced an unobvious choice. Several
  workarounds here look arbitrary and are not — say which failure they prevent.
- README documents user-facing behaviour; update it when behaviour changes.
