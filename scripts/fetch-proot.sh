#!/usr/bin/env bash
# Fetch the aarch64 proot binary (+ libtalloc) into app/src/main/jniLibs.
# These are gitignored; run this once before a build that needs a working
# bootstrap. assembleDebug succeeds without them. See jniLibs/README.md.
#
# Source: UserLAnd prebuilt assets (GPL-compatible), an NDK r18 aarch64 PIE.
#   github.com/CypherpunkArmory/UserLAnd-Assets-Support, assets/arm64/
set -euo pipefail

BASE="https://raw.githubusercontent.com/CypherpunkArmory/UserLAnd-Assets-Support/staging/assets/arm64"
DEST="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$DEST"

echo "Fetching proot -> $DEST/libproot.so"
curl -fsSL "$BASE/proot" -o "$DEST/libproot.so"

echo "Fetching libtalloc.so.2"
curl -fsSL "$BASE/libtalloc.so.2" -o "$DEST/libtalloc.so"

# proot's DT_NEEDED names libtalloc.so.2, but the APK packager only ships
# files ending in .so. Rewrite the dependency to the packaged name. Needs
# patchelf (apt-get install patchelf). If patchelf is unavailable this
# leaves a note; the build still succeeds, proot just won't link at runtime
# until this is resolved (see jniLibs/README.md gotcha #1).
if command -v patchelf >/dev/null 2>&1; then
    patchelf --replace-needed libtalloc.so.2 libtalloc.so "$DEST/libproot.so"
    echo "patched proot DT_NEEDED: libtalloc.so.2 -> libtalloc.so"
else
    echo "WARNING: patchelf not found - proot still references libtalloc.so.2."
    echo "         Install patchelf and re-run, or build a static proot."
fi

echo "Done. Bundled:"
ls -la "$DEST"/*.so
