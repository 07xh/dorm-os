package io.github.x07xh.xiaoxu.data

import java.net.URI

/**
 * Display-only defense in depth for untrusted cloud links.
 *
 * The owner-scoped backend is authoritative; malformed entries must not turn
 * into non-HTTPS intents or prevent unrelated tasks from being displayed.
 * No networking, preview, redirect resolution or link rewriting happens here.
 */
internal fun safeCloudLinks(values: List<String>): List<String> {
    val safe = LinkedHashSet<String>()
    for (value in values.take(3)) {
        if (value.length !in 12..512 || value != value.trim() ||
            value.any { it.code < 0x20 || it.code == 0x7f }) continue
        val uri = runCatching { URI(value) }.getOrNull() ?: continue
        if (uri.scheme != "https" || uri.host.isNullOrBlank() ||
            uri.rawUserInfo != null || uri.port !in -1..65535) continue
        safe.add(value)
    }
    return safe.toList()
}
