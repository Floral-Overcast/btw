package com.floralovercast.btw.bootstrap

/**
 * Where the Arch Linux ARM rootfs comes from.
 *
 * ALARM publishes a single rolling "latest" tarball (~800 MB) and a
 * companion `.md5` next to it. The checksum can't be hardcoded - it is
 * fetched at runtime and compared.
 *
 * Transport: the official geo-redirect (`mirror.archlinuxarm.org`) and the
 * `*.mirror.archlinuxarm.org` subdomains serve a TLS cert that does not
 * cover their hostnames, so https to them fails verification and they're
 * http-only. We instead prefer full-mirror hosts that DO present a valid
 * https cert, so the download runs over TLS and the md5 is tamper-resistant
 * (as far as TLS to the mirror goes), not just corruption detection. See
 * docs/architecture.md "Rootfs download" for the full reasoning.
 *
 * [MIRRORS] is tried in order; the first that serves the tarball wins. Two
 * entries, not one, because this is the single download the whole app
 * depends on - a mirror being briefly down shouldn't brick first run. Both
 * are verified-https as of 2026-10; add more only if these prove flaky.
 */
object RootfsSource {

    /** Mirror base URLs (no trailing slash), highest preference first. */
    val MIRRORS = listOf(
        "https://mirrors.dotsrc.org/archlinuxarm",
        "https://de3.mirror.archlinuxarm.org",
    )

    private const val TARBALL_PATH = "/os/ArchLinuxARM-aarch64-latest.tar.gz"

    /** Tarball URL on a given mirror base. */
    fun tarballUrl(mirror: String): String = "$mirror$TARBALL_PATH"

    /** Companion `.md5` URL on a given mirror base. */
    fun checksumUrl(mirror: String): String = "$mirror$TARBALL_PATH.md5"

    /** Local filename for the downloaded tarball, under cacheDir. */
    const val TARBALL_NAME = "ArchLinuxARM-aarch64-latest.tar.gz"
}
