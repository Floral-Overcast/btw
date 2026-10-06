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

- APK bundles a static aarch64 `proot` in jniLibs (exec-legal location).
- First run: pick mirror -> download Arch Linux ARM aarch64 tarball ->
  verify checksum -> extract to app-private storage (proot handles the
  no-root ownership problem).
- Post-extract setup, scripted: pacman keyring init, locale, a default
  user, resolv.conf.
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
3. Rootfs delta updates vs plain `pacman -Syu` (probably just pacman).
4. Whether Termux's terminal-view can be consumed as a library without
   dragging in the rest of Termux.

## Test devices in the fleet

- AYN Thor (Snapdragon 8 Gen 2, Adreno 740) - full stack incl. Turnip.
- Any Mali device in the house - software-path regression check.
