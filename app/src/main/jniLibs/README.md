# jniLibs - native binaries bundled in the APK

Everything Android itself has to `exec()` must live here, in the APK's
native library directory. That is the one place a `targetSdk 28` app can
run a binary from on Android 10+ (the "exec wall"; see CLAUDE.md). The
rootfs binaries do NOT go here - they run *through* proot, which is the
only thing Android execs directly.

These files are **gitignored** (`jniLibs/**/*.so`) and never committed:
they are fetched by `scripts/fetch-proot.sh` before a build that needs
them. `assembleDebug` works without them (the app just reports proot
missing when you tap Install).

## What belongs here

`arm64-v8a/` (the only ABI we ship for now):

- `libproot.so` - the proot binary, renamed. Must be an aarch64 PIE built
  against bionic (Android's libc), not a glibc static build.
- `libtalloc.so.2` ... see the soname gotcha below.

## Where proot comes from

No clean upstream ships a drop-in static aarch64 proot. The pragmatic,
GPL-compatible source is UserLAnd's prebuilt assets
(`github.com/CypherpunkArmory/UserLAnd-Assets-Support`, `assets/arm64/`):
an NDK r18 aarch64 PIE. `fetch-proot.sh` pulls from there as a starting
point. DroidDeck's proot (open question #2 in docs/architecture.md) is the
other reference once we need their phantom-killer/seccomp patches.

## Packaging gotchas (real, verified)

1. The APK packager only includes files whose names end in `.so`. proot's
   companion `libtalloc.so.2` ends in `.2`, so it is NOT packaged as-is.
   proot's `DT_NEEDED` entry is literally `libtalloc.so.2`, so simply
   renaming it to `libtalloc.so` breaks the link. Resolution (not yet
   applied): `patchelf --replace-needed libtalloc.so.2 libtalloc.so` on
   proot and ship `libtalloc.so`, OR build a fully static proot with no
   libtalloc dependency. `fetch-proot.sh` documents and attempts the
   patchelf path.
2. `android:extractNativeLibs="true"` (set in the manifest) is required:
   compressed/in-place libs can't be exec'd; they must be extracted to
   `nativeLibraryDir` on install.
3. proot also needs `liblog.so`/`libdl.so`/`libc.so` - those are Android
   system libraries, always present, nothing to bundle.
