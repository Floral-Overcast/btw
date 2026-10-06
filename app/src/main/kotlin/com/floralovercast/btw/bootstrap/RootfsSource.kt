package com.floralovercast.btw.bootstrap

/**
 * Where the Arch Linux ARM rootfs comes from.
 *
 * ALARM publishes a single rolling "latest" tarball (~800 MB) and a
 * companion `.md5` next to it. The checksum therefore can't be hardcoded -
 * it is fetched at runtime and compared. (ALARM ships MD5, not SHA256;
 * this is integrity against a truncated/corrupt download, not a security
 * guarantee - the download itself is plain http from a mirror.)
 *
 * The base URL 302-redirects to a geographically chosen mirror, most of
 * which are http-only, which is why the manifest sets usesCleartextTraffic.
 */
object RootfsSource {
    const val TARBALL_URL =
        "http://os.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz"
    const val CHECKSUM_URL = "$TARBALL_URL.md5"

    /** Local filename for the downloaded tarball, under cacheDir. */
    const val TARBALL_NAME = "ArchLinuxARM-aarch64-latest.tar.gz"
}
