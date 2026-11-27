package app.hn2.util

import java.net.URI

/** Score is "hot" at 3x the comment threshold, matching Materialistic. */
fun isHotScore(score: Int, threshold: Int) = score >= threshold * 3

fun isHotComments(count: Int, threshold: Int) = count >= threshold

/** "5m", "12h", "3d", "2y": the compact age used throughout the UI. */
fun relativeTime(epochSeconds: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val seconds = (nowMillis / 1000 - epochSeconds).coerceAtLeast(0)
    return when {
        seconds < 60 -> "now"
        seconds < 3_600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3_600}h"
        seconds < 31_536_000 -> "${seconds / 86_400}d"
        else -> "${seconds / 31_536_000}y"
    }
}

/** "https://www.example.com/a/b" -> "example.com". */
fun domainOf(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val host = runCatching { URI(url).host }.getOrNull() ?: return null
    return host.removePrefix("www.")
}

fun hnItemUrl(id: Long) = "https://news.ycombinator.com/item?id=$id"
