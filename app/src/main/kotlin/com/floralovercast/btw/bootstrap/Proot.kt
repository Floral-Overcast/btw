package com.floralovercast.btw.bootstrap

import android.content.Context
import java.io.File

/**
 * Locates the bundled proot binary and (later) builds commands that run
 * rootfs programs through it.
 *
 * proot is exec'd from the APK's nativeLibraryDir - the only exec-legal
 * location for a targetSdk 28 app on modern Android (CLAUDE.md exec wall).
 * It is shipped as `libproot.so`; see jniLibs/README.md for how it gets
 * there and the libtalloc soname gotcha.
 */
object Proot {

    private const val BINARY = "libproot.so"

    /** The proot binary, or null if it wasn't bundled (fetch-proot.sh). */
    fun binary(context: Context): File? {
        val f = File(context.applicationInfo.nativeLibraryDir, BINARY)
        return if (f.exists()) f else null
    }

    fun isBundled(context: Context): Boolean = binary(context) != null

    /**
     * Command to run a program inside the extracted rootfs under proot.
     * Stub: assembles the argv we expect to need; not executed yet. The
     * post-extract setup (keyring, locale, default user, resolv.conf) will
     * run through this. See docs/architecture.md layer 1.
     */
    fun command(context: Context, rootfs: File, vararg argv: String): List<String> {
        val proot = binary(context)?.absolutePath
            ?: error("proot not bundled; run scripts/fetch-proot.sh")
        return buildList {
            add(proot)
            add("-r"); add(rootfs.absolutePath)   // new root
            add("-0")                               // fake root (uid 0)
            add("-w"); add("/root")                 // working dir
            add("/bin/sh"); add("-l")
            addAll(argv)
        }
    }
}
