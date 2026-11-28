package app.hn2.util

import java.text.BreakIterator
import java.util.Locale

/**
 * Splits text into chunks for text-to-speech. Chunks only ever end at a sentence or paragraph
 * boundary, so the voice never pauses mid-sentence; paragraphs never share a chunk.
 *
 * Whole sentences are merged while the chunk stays under a limit that starts at [firstMaxChars] and
 * doubles with each chunk up to [maxChars]: playback can start as soon as the short first chunk is
 * ready, and each chunk is generated while the shorter one before it plays. A sentence longer than
 * the limit becomes a chunk of its own rather than being cut. Only a sentence longer than
 * [hardMaxChars] (a run-on with no full stops, like a list or code) is split, at a comma or
 * similar if possible.
 */
fun speechChunks(
    text: String,
    maxChars: Int = 300,
    firstMaxChars: Int = 60,
    hardMaxChars: Int = 1000,
    locale: Locale = Locale.getDefault(),
): List<String> {
    val chunks = mutableListOf<String>()
    fun limit() = (firstMaxChars.toLong() shl chunks.size.coerceAtMost(16)).coerceAtMost(maxChars.toLong()).toInt()
    for (paragraph in text.split('\n')) {
        val current = StringBuilder()
        fun flush() {
            if (current.isNotBlank()) chunks += current.toString().trim()
            current.clear()
        }
        for (sentence in sentences(paragraph, locale).flatMap { splitRunOn(it, hardMaxChars) }) {
            if (current.isNotEmpty() && current.length + 1 + sentence.length > limit()) flush()
            if (current.isNotEmpty()) current.append(' ')
            current.append(sentence)
        }
        flush()
    }
    return chunks
}

// The sentence splitter treats the full stop in these as the end of a sentence.
private val ABBREVIATION = Regex(
    "(?:\\b(?:Mr|Mrs|Ms|Dr|Prof|Sr|Jr|St|vs|etc|e\\.g|i\\.e|Inc|Ltd|Co|Corp|No|Fig|approx|U\\.S|U\\.K)|\\b[A-Z])\\.$",
    RegexOption.IGNORE_CASE,
)

internal fun sentences(paragraph: String, locale: Locale): List<String> {
    val iterator = BreakIterator.getSentenceInstance(locale)
    iterator.setText(paragraph)
    val result = mutableListOf<String>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        val sentence = paragraph.substring(start, end).trim()
        if (sentence.isNotEmpty()) {
            // "Mr. Smith" and "J. R. R. Tolkien" aren't sentence ends: join with what came before.
            val previous = result.lastOrNull()
            if (previous != null && ABBREVIATION.containsMatchIn(previous)) {
                result[result.lastIndex] = "$previous $sentence"
            } else {
                result += sentence
            }
        }
        start = end
        end = iterator.next()
    }
    return result
}

/** Splits a sentence over [maxChars] after a comma, semicolon, colon or dash, or else between words. */
private fun splitRunOn(sentence: String, maxChars: Int): List<String> {
    if (sentence.length <= maxChars) return listOf(sentence)
    val window = sentence.substring(0, maxChars)
    val cut = Regex("[,;:—–](?=\\s)").findAll(window).lastOrNull()?.range?.last?.plus(1)
        ?: window.lastIndexOf(' ').takeIf { it > 0 }
        ?: maxChars
    return listOf(sentence.substring(0, cut).trim()) + splitRunOn(sentence.substring(cut).trim(), maxChars)
}

/** Collapses the whitespace in extracted article text, keeping one line break between paragraphs. */
fun normalizeArticleText(text: String): String = text
    .replace('\u00A0', ' ')
    .split('\n')
    .map { it.replace(Regex("\\s+"), " ").trim() }
    .filter { it.isNotEmpty() }
    .joinToString("\n")
