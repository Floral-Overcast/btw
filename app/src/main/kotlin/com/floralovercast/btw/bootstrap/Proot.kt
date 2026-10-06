package com.floralovercast.btw.bootstrap

import android.content.Context
import java.io.File

/**
 * Locates the bundled proot binary and builds/runs the commands that drive
 * the rootfs through it.
 *
 * proot is exec'd from the APK's nativeLibraryDir - the only exec-legal
 * location for a targetSdk 28 app on modern Android (CLAUDE.md exec wall).
 * It ships as `libproot.so`; see jniLibs/README.md for how it gets there
 * and the libtalloc soname gotcha.
 *
 * Two proot shapes are used:
 *  - EXTRACT: guest root stays the host `/`, and we run Android's own
 *    toybox `tar` through proot purely to get `--link2symlink` (hardlinks
 *    become symlinks, which app-private storage/SELinux won't otherwise
 *    allow) and faked root for ownership. This is how proot-distro and
 *    UserLAnd unpack a rootfs unrooted.
 *  - GUEST SHELL: guest root is the extracted rootfs, run as fake uid 0.
 *
 * NOTE: none of this has been exercised on an aarch64 device from this
 * build host (x86-64 CT can't run the binaries). The argv/env below is the
 * best-reasoned form; the on-device smoke test is the real check.
 */
object Proot {

    private const val BINARY = "libproot.so"

    /** Android's bundled tar (toybox). Present since API 23; we pin 28. */
    private const val HOST_TAR = "/system/bin/tar"

    /** The proot binary, or null if it wasn't bundled (fetch-proot.sh). */
    fun binary(context: Context): File? {
        val f = File(context.applicationInfo.nativeLibraryDir, BINARY)
        return if (f.exists()) f else null
    }

    fun isBundled(context: Context): Boolean = binary(context) != null

    private fun prootPath(context: Context): String =
        binary(context)?.absolutePath
            ?: error("proot not bundled; run scripts/fetch-proot.sh")

    /** proot needs a writable, exec-capable scratch dir for its loader. */
    private fun tmpDir(context: Context): File =
        File(context.filesDir, "proot-tmp").apply { mkdirs() }

    /**
     * argv to extract [tarball] into [destDir] via host tar under proot.
     * Flags kept to the subset common to toybox/busybox/GNU tar (x z f C) -
     * notably no `-p`/`--preserve-permissions` (toybox restores perms by
     * default and rejects the long flag). ALARM's tarball unpacks to top
     * level (bin/, usr/, ...) with NO leading dir, so no --strip-components.
     */
    fun extractCommand(context: Context, tarball: File, destDir: File): List<String> =
        listOf(
            prootPath(context),
            "-r", "/",             // guest root = host; identity path mapping
            "-0",                  // fake uid 0 so tar's chown/chmod "succeed"
            "--link2symlink",      // hardlinks -> symlinks (no-root, SELinux)
            HOST_TAR,
            "-x", "-z",
            "-f", tarball.absolutePath,
            "-C", destDir.absolutePath,
        )

    /**
     * argv to run [scriptPath] (a guest-absolute path, e.g. /tmp/x.sh) with
     * /bin/sh inside [rootfs] as fake root. Binds the kernel filesystems the
     * guest expects; these are host paths proot maps into the guest.
     */
    fun shellScriptCommand(context: Context, rootfs: File, scriptPath: String): List<String> =
        buildList {
            add(prootPath(context))
            add("-r"); add(rootfs.absolutePath)
            add("-0")
            add("-w"); add("/root")
            add("-b"); add("/dev")
            add("-b"); add("/proc")
            add("-b"); add("/sys")
            add("/bin/sh"); add(scriptPath)
        }

    /** Default env for a proot invocation. */
    fun env(context: Context): Map<String, String> = mapOf(
        "PROOT_TMP_DIR" to tmpDir(context).absolutePath,
        "HOME" to "/root",
        "TERM" to "xterm-256color",
        "LANG" to "C.UTF-8",
        "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin",
    )

    /**
     * Run [argv], merge stderr into stdout, append every line to [log], and
     * return the exit code. Blocking; call off the main thread.
     */
    fun run(
        context: Context,
        argv: List<String>,
        extraEnv: Map<String, String> = emptyMap(),
        log: (String) -> Unit = {},
    ): Int {
        val pb = ProcessBuilder(argv).redirectErrorStream(true)
        pb.environment().putAll(env(context))
        pb.environment().putAll(extraEnv)
        val proc = pb.start()
        proc.inputStream.bufferedReader().useLines { lines ->
            lines.forEach(log)
        }
        return proc.waitFor()
    }
}
