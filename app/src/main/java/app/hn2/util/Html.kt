package app.hn2.util

/** A piece of HN comment HTML: either inline-formatted text or a `<pre><code>` block. */
sealed interface HtmlBlock {
    data class Text(val html: String) : HtmlBlock
    data class Code(val code: String) : HtmlBlock
}

private val CODE_BLOCK = Regex("<pre>\\s*<code>(.*?)</code>\\s*</pre>", RegexOption.DOT_MATCHES_ALL)

/**
 * Splits HN HTML into text and code blocks. Code keeps its whitespace and has entities decoded,
 * since an HTML-to-text converter would collapse the indentation.
 */
fun parseHtmlBlocks(html: String): List<HtmlBlock> {
    val blocks = mutableListOf<HtmlBlock>()
    var cursor = 0
    for (match in CODE_BLOCK.findAll(html)) {
        html.substring(cursor, match.range.first).toTextBlock()?.let(blocks::add)
        blocks += HtmlBlock.Code(decodeEntities(match.groupValues[1]).trimEnd('\n'))
        cursor = match.range.last + 1
    }
    html.substring(cursor).toTextBlock()?.let(blocks::add)
    return blocks
}

/**
 * HN separates paragraphs with a bare `<p>`. Turning those into double line breaks gives the same
 * spacing whichever HTML mode the renderer uses.
 */
private fun String.toTextBlock(): HtmlBlock.Text? {
    val normalized = replace("</p>", "")
        .replace("<p>", "<br><br>")
        .trimStartBreaks()
        .trimEndBreaks()
    return if (normalized.isBlank()) null else HtmlBlock.Text(normalized)
}

private fun String.trimStartBreaks(): String {
    var s = this.trimStart()
    while (s.startsWith("<br>")) s = s.removePrefix("<br>").trimStart()
    return s
}

private fun String.trimEndBreaks(): String {
    var s = this.trimEnd()
    while (s.endsWith("<br>")) s = s.removeSuffix("<br>").trimEnd()
    return s
}

private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")
private val NAMED = mapOf("lt" to "<", "gt" to ">", "amp" to "&", "quot" to "\"", "apos" to "'", "nbsp" to " ")

fun decodeEntities(text: String): String = ENTITY.replace(text) { match ->
    val body = match.groupValues[1]
    when {
        body.startsWith("#x") -> body.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
        body.startsWith("#") -> body.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
        else -> NAMED[body]
    } ?: match.value
}
