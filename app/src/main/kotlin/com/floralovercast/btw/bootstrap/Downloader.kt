package com.floralovercast.btw.bootstrap

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Dumb-simple HTTP(S) download. HttpURLConnection follows redirects on its
 * own. No retries or resume yet - add them only if the plain path proves
 * flaky (CLAUDE.md: dumb-simple first). Mirror fallback lives here only as a
 * thin "try each until one serves bytes" loop, because the rootfs download
 * is the one fetch the whole app depends on.
 */
object Downloader {

    /** Progress callback: (bytesSoFar, totalBytes or -1 if unknown). */
    fun interface Progress {
        fun onProgress(soFar: Long, total: Long)
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 30_000
        }

    fun download(url: String, dest: File, progress: Progress? = null) {
        val conn = open(url)
        try {
            if (conn.responseCode !in 200..299) {
                throw IOException("HTTP ${conn.responseCode} for $url")
            }
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
        val conn = open(url)
        return try {
            if (conn.responseCode !in 200..299) {
                throw IOException("HTTP ${conn.responseCode} for $url")
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Try [mirrors] in order, mapping each to a URL via [urlFor], until one
     * downloads cleanly. Returns the mirror that worked. Throws with every
     * mirror's error attached if all fail.
     */
    fun downloadFromFirst(
        mirrors: List<String>,
        urlFor: (String) -> String,
        dest: File,
        progress: Progress? = null,
    ): String {
        val failures = StringBuilder()
        for (mirror in mirrors) {
            try {
                download(urlFor(mirror), dest, progress)
                return mirror
            } catch (e: IOException) {
                failures.append("\n  $mirror: ${e.message}")
                dest.delete()
            }
        }
        throw IOException("all mirrors failed:$failures")
    }
}
