# btw - Arch Linux on Android

Single-APK Arch Linux ARM environment for unrooted Android. Public repo
(`Floral-Overcast/btw`), GPL-3.0, distributed via GitHub Releases;
Play Store is a maybe-later (see gotchas).

## What it is

An Android app that bootstraps an Arch Linux ARM rootfs under proot and
gives the user a terminal (v0.1), then an in-app Wayland compositor for
graphical sessions (v0.2), then Turnip GPU acceleration on Adreno (v0.3).
Full plan and competitive landscape: `docs/architecture.md`.

Positioning: not "first Arch on Android" (Termux/UserLAnd/Andronix exist),
but the first one that is actually nice to use. UX polish IS the product.

## Stack

- Kotlin, single-module Android app, Gradle.
- `minSdk 28`, **`targetSdk 28`** - deliberate, do not bump. Targeting 29+
  blocks exec() of downloaded binaries from app data (the Termux wall).
- Native bits: aarch64 `proot` bundled in `jniLibs` (ships inside the APK
  so exec-from-native-lib-dir stays legal on newer Android). Plus
  `libtermux.so`, the PTY JNI, built via ndkBuild from vendored Termux C
  (`app/src/main/jni/`); needs the NDK (`ndk;26.1.10909125`).
- Rootfs: official Arch Linux ARM aarch64 tarball, downloaded on first
  run from mirrors, checksum-verified, extracted to app-private storage.
  Never bundled in the APK, never committed to git.

## Where dev happens

Active feature work runs in worker CT `btw` (CT229 on Seolla), which
pushes to `main`. The cloud Claude Space checkout only mirrors
`origin/main`. Don't build features from the cloud checkout while the
CT is active; dispatch to the CT instead.

## Repo layout

- `docs/architecture.md` - the plan, layer by layer, with the open
  research questions. Keep it current when decisions change.
- `app/` - Android app (created by the worker; standard Gradle layout).
  `app/src/main/java/com/termux/` + `app/src/main/jni/` are vendored Termux
  terminal code (GPL-3.0); don't hand-edit except to re-point resource
  imports. Provenance in `third_party/termux/`.

## Build / run

Worker CT owns builds (headless Gradle + Android cmdline-tools + NDK
`26.1.10909125` for the PTY JNI). From the host: brief and dispatch, don't
hand-edit here. Smoke test = APK installs
and reaches a pacman-capable shell on a real device (Thor or a Snapdragon
phone over adb).

## Conventions

- Reference code: DroidDeck and Termux repos are GPL like us - reading
  AND reusing with attribution is fine. Note origin in a comment when a
  nontrivial chunk is derived.
- Dumb-simple first: no services, watchdogs, or retry machinery until the
  plain path demonstrably fails.
- User-facing copy is plain and non-salesy; it describes, doesn't perform.

## Gotchas

- **Phantom process killer** (Android 12+): kills forked children of
  apps. DroidDeck and Termux both live with this; copy their mitigation
  (foreground service + user-facing one-time `adb` toggle doc), don't
  invent one.
- **Play Store**: requires current targetSdk, which conflicts with the
  exec restriction above. GitHub-first is a constraint, not a preference.
- **W^X / SELinux**: all executables must ship in the APK's native lib
  dir; the rootfs binaries run THROUGH proot (which is in jniLibs), so
  only proot itself needs to be exec'd by Android.
- Mali/Xclipse devices: proot + terminal must still work (software path);
  only GPU accel is Adreno-gated.
