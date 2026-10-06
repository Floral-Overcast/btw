package com.floralovercast.btw.bootstrap

import android.content.Context
import java.io.File

/**
 * Layer 1 bootstrap flow (docs/architecture.md): download the Arch Linux
 * ARM rootfs, verify it, extract it, run first-time setup, then we have a
 * shell. This is the groundwork skeleton - the phases are wired in order
 * and the download/verify helpers are real, but extract + setup are TODO
 * (they need the bundled proot, which fetch-proot.sh supplies; see
 * jniLibs/README.md). Nothing here is called from the UI yet beyond
 * [preflight].
 */
object Bootstrap {

    sealed interface Phase {
        data object CheckProot : Phase
        data class Download(val soFar: Long, val total: Long) : Phase
        data object Verify : Phase
        data object Extract : Phase
        data object Setup : Phase
        data object Done : Phase
    }

    fun rootfsDir(context: Context): File = File(context.filesDir, "rootfs")

    private fun tarball(context: Context): File =
        File(context.cacheDir, RootfsSource.TARBALL_NAME)

    /** True once a rootfs has been extracted (cheap check: /bin exists). */
    fun isInstalled(context: Context): Boolean =
        File(rootfsDir(context), "bin").isDirectory

    /**
     * A one-line status for the Install button to show today, before the
     * full flow is wired. Proves the native-lib plumbing end to end without
     * pulling the ~800 MB tarball.
     */
    fun preflight(context: Context): String = when {
        isInstalled(context) -> "Rootfs already installed."
        !Proot.isBundled(context) ->
            "proot not bundled yet - run scripts/fetch-proot.sh and rebuild."
        else -> "proot ready. Rootfs download not wired up yet."
    }

    /**
     * The full flow, phase by phase. Not invoked from the UI yet; it will
     * run on a background thread once extract + setup land.
     */
    fun install(context: Context, onPhase: (Phase) -> Unit) {
        onPhase(Phase.CheckProot)
        check(Proot.isBundled(context)) {
            "proot not bundled; run scripts/fetch-proot.sh"
        }

        val tar = tarball(context)
        Downloader.download(RootfsSource.TARBALL_URL, tar) { soFar, total ->
            onPhase(Phase.Download(soFar, total))
        }

        onPhase(Phase.Verify)
        val expected = Checksum.parseExpected(
            Downloader.fetchText(RootfsSource.CHECKSUM_URL)
        )
        val actual = Checksum.md5(tar)
        check(actual == expected) {
            "checksum mismatch: expected $expected got $actual"
        }

        onPhase(Phase.Extract)
        extract(context, tar)

        onPhase(Phase.Setup)
        setup(context)

        onPhase(Phase.Done)
    }

    /**
     * TODO(layer1): extract the tar.gz into rootfsDir. Needs ownership and
     * symlinks preserved, which stock Android tar can't do unrooted - run
     * `tar` inside the rootfs through proot, the proot-distro way. Blocked
     * on the bundled proot (fetch-proot.sh) and on resolving the libtalloc
     * soname gotcha (jniLibs/README.md).
     */
    private fun extract(context: Context, tarball: File): Unit =
        TODO("extract rootfs via proot - see docs/architecture.md layer 1")

    /**
     * TODO(layer1): first-run setup scripted through proot - pacman keyring
     * init, locale, a default user, resolv.conf. Zero terminal commands
     * between Install and a working `pacman -Syu`.
     */
    private fun setup(context: Context): Unit =
        TODO("post-extract setup via proot - see docs/architecture.md layer 1")
}
