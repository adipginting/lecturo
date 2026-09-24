package com.adipginting.lecturo.library

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.Closeable

/** What arrived: the format its bytes prove, where it came from, and the body to drain. */
internal class Fetched(
    val format: String,
    val url: String,
    val body: ResponseBody,
) : Closeable {
    override fun close() = body.close()
}

/**
 * Fetches a book over HTTP and insists that what arrives is the book.
 *
 * A book URL does not always answer with the book. Standard Ebooks serves a
 * "Your Download Has Started!" page that points at the same URL carrying
 * `?source=download`, and saving that page under an EPUB name is what left the
 * library holding a file the reader could not open. So the bytes decide the
 * format, a page is followed to the file it stands in for, and a payload that
 * is still not a book is refused with the reason.
 */
internal class BookFetch(private val client: OkHttpClient = OkHttpClient()) {

    fun fetch(url: String, headers: Map<String, String> = emptyMap()): Fetched =
        fetch(url, headers, hops = 0)

    private fun fetch(url: String, headers: Map<String, String>, hops: Int): Fetched {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .apply { headers.forEach { (name, value) -> header(value = value, name = name) } }
            .build()
        val response = client.newCall(request).execute()
        val body = response.body
        if (!response.isSuccessful || body == null) {
            val code = response.code
            response.close()
            throw IllegalArgumentException("HTTP $code from $url")
        }

        // The header is peeked through a copy of the buffer, leaving the body
        // whole for the caller to save.
        val source = body.source()
        source.request(BookFile.HEADER_BYTES.toLong())
        val header = source.buffer.copy().readByteArray(source.buffer.size)
        BookFile.sniff(header)?.let { return Fetched(it, url, body) }

        val forward = if (BookFile.isWebPage(header) && hops < MAX_HOPS) {
            source.request(GATEWAY_BYTES.toLong())
            metaRefreshUrl(source.buffer.copy().readUtf8(source.buffer.size))
        } else {
            null
        }
        body.close()

        val target = forward?.let { response.request.url.resolve(it)?.toString() }
            ?: throw IllegalArgumentException(BookFile.describe(header, url))
        // A redirect to another host must not carry the credentials meant for
        // this one — OPDS catalogues authenticate with headers.
        val onward = if (target.toHttpUrlOrNull()?.host == response.request.url.host) headers else emptyMap()
        return fetch(target, onward + ("Referer" to url), hops + 1)
    }

    companion object {
        /**
         * Names this app rather than impersonating a browser, while still being
         * shaped like one: some gateways only serve the file to a known client.
         */
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Lecturo/1.0"

        /** A download page is small; anything larger is not one. */
        private const val GATEWAY_BYTES = 256 * 1024L

        /** Gateways chain at most a couple of times before the real file. */
        private const val MAX_HOPS = 3

        private val META_REFRESH =
            Regex("""(?is)<meta[^>]*http-equiv\s*=\s*["']?\s*refresh["']?[^>]*>""")
        private val REFRESH_URL = Regex("""(?is)url\s*=\s*["']?([^"'\s>]+)""")

        /**
         * The URL a download page points at, read from its `<meta http-equiv=refresh>`.
         * Reaching for a pattern to read markup is usually a poor idea; a meta
         * refresh is a machine-readable instruction, which is the exception.
         */
        fun metaRefreshUrl(html: String): String? {
            val tag = META_REFRESH.find(html)?.value ?: return null
            val raw = REFRESH_URL.find(tag)?.groupValues?.get(1) ?: return null
            return raw.replace("&amp;", "&")
        }
    }
}
