package com.floralovercast.btw.bootstrap

import java.io.File
import java.security.MessageDigest

/** MD5 of a file, matching the format ALARM publishes in its .md5 files. */
object Checksum {

    fun md5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * ALARM .md5 files are "<hash>  <filename>". Pull just the hash so a
     * comparison ignores the filename column.
     */
    fun parseExpected(md5FileContents: String): String =
        md5FileContents.trim().substringBefore(' ').lowercase()
}
