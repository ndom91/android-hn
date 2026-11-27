package app.hn2.util

/**
 * What a signed-in HN page says about one item's vote: the [auth] token its vote links need, and
 * whether you've already upvoted it. The token is the same for voting and unvoting.
 */
data class VoteState(val auth: String, val voted: Boolean)

// Matches a whole opening <a> tag with the given id prefix, e.g. <a id='up_123' class='clicky' href='…'>.
private fun linkTag(prefix: String) = Regex("""<a\s[^>]*\bid=['"]${prefix}_(\d+)['"][^>]*>""")
private val UP_LINK = linkTag("up")
private val UNVOTE_LINK = linkTag("un")
private val AUTH = Regex("""[?&;]auth=([^&'"]+)""")

/**
 * Reads every vote link on a page. Signed-out pages have links without tokens and yield nothing.
 * HN hides the upvote arrow ("nosee") and adds an "unvote" link once you've voted.
 */
fun parseVotes(html: String): Map<Long, VoteState> {
    val unvoted = UNVOTE_LINK.findAll(html).associate { it.groupValues[1].toLong() to it.value }
    val votes = mutableMapOf<Long, VoteState>()
    for (match in UP_LINK.findAll(html)) {
        val id = match.groupValues[1].toLong()
        val unvote = unvoted[id]
        val auth = AUTH.find(match.value)?.groupValues?.get(1)
            ?: unvote?.let { AUTH.find(it)?.groupValues?.get(1) }
            ?: continue
        votes[id] = VoteState(auth, voted = unvote != null || "nosee" in match.value)
    }
    return votes
}

/** The value of a hidden form field, such as the `hmac` that HN's comment forms carry. */
fun parseHiddenInput(html: String, name: String): String? =
    Regex("""<input[^>]*\bname=['"]$name['"][^>]*>""").find(html)?.value
        ?.let { Regex("""\bvalue=['"]([^'"]*)['"]""").find(it)?.groupValues?.get(1) }
        ?.takeIf { it.isNotEmpty() }

/** Signed-in pages link to your profile from the top bar as `<a id='me' …>`. */
fun isSignedIn(html: String): Boolean = Regex("""\bid=['"]me['"]""").containsMatchIn(html)

private val TAG = Regex("<[^>]+>")
private val SCRIPT_OR_STYLE = Regex("<(script|style)[^>]*>.*?</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
private val SPACES = Regex("\\s+")

/**
 * HN reports most errors ("You're posting too fast", "Bad login") as a bare page of text. This
 * returns that text, or null if the page is a full HN page with nothing short to say.
 */
fun pageMessage(html: String): String? {
    val text = decodeEntities(TAG.replace(SCRIPT_OR_STYLE.replace(html, " "), " "))
        .replace(SPACES, " ")
        .trim()
    return text.takeIf { it.isNotEmpty() && it.length <= 300 }
}
