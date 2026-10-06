# Vendored Termux terminal (GPL-3.0)

btw embeds a terminal emulator by vendoring two modules from
[termux/termux-app](https://github.com/termux/termux-app):
`terminal-emulator` and `terminal-view`. They give us a battle-tested VT100
state machine, a rendering View, and a PTY JNI; we feed the session our own
`proot` as the shell (see `TerminalActivity`), not Termux's shell stack.

## Provenance

- Source: https://github.com/termux/termux-app
- Vendored at tag: **v0.118.3**
- License: **GPL-3.0-only** (`LICENSE.md` here, copied verbatim). The two
  modules also incorporate code from jackpal's Android Terminal Emulator,
  originally Apache-2.0; `LICENSE.md` records that exception. btw is itself
  GPL-3.0, so this is compatible and our public repo satisfies the
  source-availability obligation.

The upstream `.java`/`.c` files carry no per-file copyright header, so there
is nothing to preserve per file beyond this notice and the license copy.

## What was copied, and where it lives

Package names were kept so imports match upstream:

- `terminal-emulator/src/main/java/com/termux/terminal/*.java`
  -> `app/src/main/java/com/termux/terminal/`  (14 files)
- `terminal-view/src/main/java/com/termux/view/**/*.java`
  -> `app/src/main/java/com/termux/view/`  (view + textselection/, 7 files)
- `terminal-emulator/src/main/jni/{termux.c,Android.mk}`
  -> `app/src/main/jni/`  (builds `libtermux.so`, the PTY JNI)

Only dependency is `androidx.annotation`; nothing drags in `termux-shared`.
The ndkBuild wiring is in `app/build.gradle.kts`.

## Updating

Re-fetch the same relative paths from a newer tag, diff, and bump the tag
above. If upstream adds files (e.g. the sixel/image support present on
`master`: TerminalBitmap/TerminalSixel/ITermImage), pull those too or the
emulator won't compile.
