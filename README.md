# btw

Arch Linux on Android, btw.

One APK that gives you a real Arch Linux ARM environment on a stock,
unrooted Android phone. Install the app, tap once, get a shell with
`pacman`. No Termux setup, no scripts to paste, no VNC.

## Status

Early scaffold. Nothing to download yet. Watch the releases page.

## What it will be

- **v0.1** - single APK: bundled proot, downloads and verifies the Arch
  Linux ARM rootfs on first run, drops you into a terminal.
- **v0.2** - graphical sessions: in-app Wayland compositor rendering to a
  SurfaceView, so desktop apps run on-device instead of over VNC.
- **v0.3** - GPU acceleration on Adreno (Turnip/Vulkan, zink for GL),
  managed automatically per device.

## Why, when Termux and UserLAnd exist

Termux + proot-distro works great if you already know what those words
mean. UserLAnd ships an Arch rootfs but its app code froze in 2021 and
graphics are VNC-era. [DroidDeck](https://github.com/Droid-Deck/DroidDeck)
proved the modern stack (proot + Wayland-in-app + Turnip) is solid, but
it is aimed squarely at Steam gaming. This project is that stack, aimed
at a general Linux environment, with setup a non-expert can finish.

## Distribution

GitHub Releases first. Play Store later if the target-API exec
restrictions can be squared away (this is the same wall Termux hit;
see docs/architecture.md).

## License

GPL-3.0. Design and some plumbing reference GPL projects in this space
(DroidDeck, Termux); staying GPL keeps that clean.
