package com.floralovercast.btw.bootstrap

import android.content.Context
import com.floralovercast.btw.R
import java.io.File

/**
 * Layer 1 bootstrap flow (docs/architecture.md): download the Arch Linux
 * ARM rootfs, verify it, extract it through proot, run first-run setup,
 * then we have a shell. Blocking end to end - call [install] off the main
 * thread (MainActivity does). Progress is reported via [Phase] callbacks;
 * a full log is appended to filesDir/bootstrap.log for on-device debugging.
 *
 * UNVERIFIED on aarch64: the build host is x86-64 and cannot run proot or
 * the rootfs. The extract/setup argv is reasoned from proot-distro/UserLAnd;
 * the on-device smoke test is the real check.
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

    private fun logFile(context: Context): File =
        File(context.filesDir, "bootstrap.log")

    /** True once a rootfs has been extracted (cheap check: /bin exists). */
    fun isInstalled(context: Context): Boolean =
        File(rootfsDir(context), "bin").exists()

    /** A one-line status for the UI before/without running the full flow. */
    fun preflight(context: Context): String = when {
        isInstalled(context) -> "Rootfs already installed."
        !Proot.isBundled(context) ->
            "proot not bundled - run scripts/fetch-proot.sh and rebuild."
        else -> "Ready to install."
    }

    /**
     * The full flow, phase by phase. Blocking; run on a background thread.
     * Throws on any hard failure (no proot, all mirrors down, checksum
     * mismatch, extract failure). First-run setup is best-effort: a nonzero
     * exit is logged but doesn't fail the install, since the extracted shell
     * is still usable.
     */
    fun install(context: Context, onPhase: (Phase) -> Unit) {
        logFile(context).writeText("")
        logFile(context).appendText("btw bootstrap start\n")

        onPhase(Phase.CheckProot)
        check(Proot.isBundled(context)) {
            "proot not bundled; run scripts/fetch-proot.sh"
        }

        val tar = tarball(context)
        onPhase(Phase.Download(0, -1))
        val mirror = Downloader.downloadFromFirst(
            RootfsSource.MIRRORS, RootfsSource::tarballUrl, tar
        ) { soFar, total -> onPhase(Phase.Download(soFar, total)) }
        log(context, "downloaded from $mirror")

        onPhase(Phase.Verify)
        val expected = Checksum.parseExpected(
            Downloader.fetchText(RootfsSource.checksumUrl(mirror))
        )
        val actual = Checksum.md5(tar)
        check(actual == expected) {
            "checksum mismatch: expected $expected got $actual"
        }

        onPhase(Phase.Extract)
        extract(context, tar)

        onPhase(Phase.Setup)
        setup(context)

        tar.delete() // reclaim ~800 MB; rootfs is extracted now
        log(context, "bootstrap done")
        onPhase(Phase.Done)
    }

    /**
     * Extract the tarball into rootfsDir by running host tar through proot
     * (fake root + link2symlink). See Proot.extractCommand for why.
     */
    private fun extract(context: Context, tarball: File) {
        val dest = rootfsDir(context).apply { mkdirs() }
        val code = Proot.run(
            context, Proot.extractCommand(context, tarball, dest)
        ) { log(context, "tar: $it") }
        check(code == 0) { "extract failed: tar exited $code (see bootstrap.log)" }
        check(isInstalled(context)) { "extract produced no /bin (see bootstrap.log)" }
    }

    /**
     * First-run setup scripted through proot: resolv.conf, locale, pacman
     * keyring, a default user. Zero terminal commands between Install and a
     * working `pacman -Syu`. Best-effort (see [install]).
     */
    private fun setup(context: Context) {
        val rootfs = rootfsDir(context)

        // DNS from the host side: the guest's resolver reads this file.
        File(rootfs, "etc").mkdirs()
        File(rootfs, "etc/resolv.conf")
            .writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")

        val script = context.resources.openRawResource(R.raw.first_run_setup)
            .bufferedReader().use { it.readText() }
        val tmp = File(rootfs, "tmp").apply { mkdirs() }
        File(tmp, "btw-first-run.sh").writeText(script)

        val code = Proot.run(
            context,
            Proot.shellScriptCommand(context, rootfs, "/tmp/btw-first-run.sh"),
        ) { log(context, "setup: $it") }
        if (code != 0) {
            log(context, "first-run setup exited $code (shell still usable)")
        }
    }

    private fun log(context: Context, line: String) {
        logFile(context).appendText("$line\n")
    }
}
