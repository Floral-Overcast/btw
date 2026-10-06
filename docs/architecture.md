# Architecture

Goal: a stock, unrooted Android phone downloads one APK, taps once, and
has a usable Arch Linux ARM environment. Terminal first, desktop second,
GPU acceleration third. Each layer ships on its own.

## Landscape (why this doesn't already exist)

| Project | What it has | Why it isn't this |
|---|---|---|
| Termux + proot-distro | Maintained, `proot-distro install archlinux` works | Power-user path: F-Droid install, manual commands, separate X11 app |
| UserLAnd | Single app, wizard, Arch rootfs still updated | App code frozen since Oct 2021, VNC-era graphics, no GPU |
| Andronix | Arch ARM option, guided | Script layer on top of Termux, paywalled features |
| DroidDeck | Modern stack proven: proot + Arch ARM + Wayland-in-app + Turnip + gamescope | Laser-focused on Steam gaming, not a general environment |

DroidDeck (GPL-3.0, github.com/Droid-Deck/DroidDeck) is the primary
technical reference. We build our compositor and bootstrap clean, reading
their repo for approach; GPL-to-GPL means copying with attribution is
also legal when clean turns out to be dumb.

## Layer 1 - v0.1 terminal MVP

- APK bundles an aarch64 `proot` in jniLibs (exec-legal location). "Static"
  turned out to be aspirational: the pragmatic GPL source (UserLAnd's
  prebuilt `assets/arm64/proot`) is an NDK r18 aarch64 PIE that links
  against bionic plus one extra lib, `libtalloc.so.2`. Two consequences,
  both handled by `scripts/fetch-proot.sh` + `jniLibs/README.md`: the APK
  packager only ships `*.so`, so proot is renamed `libproot.so` and
  libtalloc's `DT_NEEDED` is patchelf'd from `libtalloc.so.2` to the
  packaged `libtalloc.so`; and `extractNativeLibs`/legacy packaging must be
  on so both get unpacked to `nativeLibraryDir` and stay exec'able. A fully
  static proot (no libtalloc) would drop the patchelf step; revisit if the
  bionic build bites us.
- First run: pick mirror -> download Arch Linux ARM aarch64 tarball ->
  verify checksum -> extract to app-private storage (proot handles the
  no-root ownership problem).
- Post-extract setup, scripted: pacman keyring init, locale, a default
  user, resolv.conf.

### Rootfs download (wired, v0.1)

- **Transport: HTTPS.** The official geo-redirect `mirror.archlinuxarm.org`
  and the `*.mirror.archlinuxarm.org` subdomains serve a TLS cert that does
  not cover their hostnames, so https to them fails verification (they are
  effectively http-only). We instead hit full-mirror hosts that present a
  valid cert. `RootfsSource.MIRRORS` (tried in order): `mirrors.dotsrc.org`
  (Danish, DOTSRC/Aalborg, range-capable, valid cert) then
  `de3.mirror.archlinuxarm.org` (that one subdomain happens to validate).
  Both verified 2026-10. Two entries, not one, because this is the single
  download the whole app depends on.
- **Why this matters for the checksum:** ALARM only publishes an MD5 next
  to the tarball (no signature). Over http that md5 is transport-integrity
  only (catches a truncated/corrupt download; a MITM could rewrite both).
  Over **https** the channel is authenticated to the mirror, so the md5 is
  now tamper-resistant as far as trusting that mirror + TLS goes - not just
  corruption detection. MD5 is cryptographically weak, but we are not handed
  anything stronger upstream, and TLS is doing the real work. If we ever
  lose every https mirror and fall back to http, this reverts to integrity
  only; document it there and set `usesCleartextTraffic` back on (it is off
  now, since all mirrors are https and the app's own networking is https).
- **targetSdk-28 cleartext note:** dropping `usesCleartextTraffic` only
  constrains the app's *own* (Java) network stack. `pacman -Syu` inside the
  guest talks over its own libc sockets through proot and is unaffected, so
  the rootfs's http mirrorlist keeps working.

### Extract + first-run setup (wired, v0.1, UNVERIFIED on-device)

- **Extract:** stock Android `tar` (toybox, `/system/bin/tar`, present
  since API 23) run *through* the bundled proot with `-0 --link2symlink`,
  guest root left as host `/`. This is the proot-distro/UserLAnd recipe:
  `-0` fakes root so tar's chown/chmod succeed, `--link2symlink` turns the
  tarball's hardlinks into symlinks (app-private storage + SELinux won't
  allow hardlinks unrooted). Flags kept to the toybox/busybox/GNU common
  subset (`x z f C`, no `-p`, no `--strip-components`: ALARM unpacks to top
  level with no leading dir).
- **Setup:** `res/raw/first_run_setup.sh` run via proot inside the extracted
  rootfs (fake root): hostname, `locale-gen` en_US.UTF-8, `pacman-key --init
  && --populate archlinuxarm` (the slow step), a default user `btw`.
  resolv.conf is written from the host side before the script runs.
  Best-effort: a nonzero script exit is logged (filesDir/bootstrap.log) but
  does not fail the install, since the extracted shell is still usable.
- **What needs the device:** none of proot/tar/pacman-key can run on the
  x86-64 build CT. Open risks for the smoke test: (a) toybox tar flag/gzip
  support varies by OEM - fallback is to bundle a busybox tar in jniLibs;
  (b) `pacman-key` gpg entropy under proot; (c) whether UserLAnd's proot
  needs a separate loader vs the embedded one (`PROOT_TMP_DIR` is set for
  it either way).
- Terminal: embed a terminal emulator view. Candidates: Termux's
  `terminal-view`/`terminal-emulator` libraries (GPL, battle-tested) vs
  writing one (don't).
- UX bar: zero terminal commands between "Install" tap and a working
  `pacman -Syu`.

## Layer 2 - v0.2 graphical sessions

- In-app Wayland compositor rendering into a SurfaceView, Android input
  translated to Wayland events. References: DroidDeck's compositor,
  Termux:X11's approach.
- Session = user picks an installed DE/app; we launch it under the
  compositor inside the existing proot.
- Software rendering (llvmpipe) is the floor so every device works;
  labwc or cage as the default session host, not a full DE, to start.
- XWayland for the long tail of X11-only apps.

## Layer 3 - v0.3 GPU acceleration

- Turnip (freedreno Vulkan) for Adreno 730+/8xx, zink on top for GL.
- Auto-detect GPU, fetch/manage the right Turnip build per device,
  DroidDeck-style. Non-Adreno devices stay on the software path.

## Hard constraints (decided)

- `targetSdk 28`: Android blocks exec() from app data dirs when
  targeting 29+. Everything Android must exec ships in the APK's native
  lib dir; rootfs binaries execute through proot. Consequence: Play
  Store (current-targetSdk mandate) is off the table until this is
  re-solved; GitHub Releases is the channel.
- Phantom process killer (Android 12+): mitigate the way Termux and
  DroidDeck do (foreground service, documented one-time adb settings
  toggle for heavy users). No custom watchdogs.
- Rootfs is never redistributed by us in v0.x: downloaded from Arch
  Linux ARM mirrors at first run, checksummed. Keeps the APK ~20 MB and
  keeps us out of the distro-mirroring business.

## Open questions (research against DroidDeck's repo)

1. How they structure the compositor (wlroots-derived? custom?) and how
   input/IME is bridged - read before writing ours.
2. Their proot fork/patches, if any (phantom-killer and seccomp fixes).
   Partial answer: we currently bundle UserLAnd's prebuilt aarch64 proot
   (bionic PIE + libtalloc); DroidDeck's build is the reference once we
   need their patches or a cleaner static binary.
3. Rootfs delta updates vs plain `pacman -Syu` (probably just pacman).
4. Whether Termux's terminal-view can be consumed as a library without
   dragging in the rest of Termux.

## Test devices in the fleet

- AYN Thor (Snapdragon 8 Gen 2, Adreno 740) - full stack incl. Turnip.
- Any Mali device in the house - software-path regression check.
