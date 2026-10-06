package com.floralovercast.btw.bootstrap

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Dumb-simple HTTP download. HttpURLConnection follows the ALARM mirror
 * redirect on its own. No retries or resume yet - add them only if the
 * plain path proves flaky (CLAUDE.md: dumb-simple first).
 */
object Downloader {

    /** Progress callback: (bytesSoFar, totalBytes or -1 if unknown). */
    fun interface Progress {
        fun onProgress(soFar: Long, total: Long)
    }

    fun download(url: String, dest: File, progress: Progress? = null) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 30_000
        }
        try {
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(1 shl 16)
                    var soFar = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        soFar += n
                        progress?.onProgress(soFar, total)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Small text fetch (e.g. the .md5 companion). */
    fun fetchText(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 30_000
        }
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
