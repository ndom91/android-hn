package app.hn2.util

import java.text.BreakIterator
import java.util.Locale

/**
 * Splits text into chunks for text-to-speech: whole sentences, merged up to [maxChars] so each
 * chunk is a natural pause point. Paragraphs never share a chunk. A sentence longer than the limit
 * is split between words, since engines slow down or refuse very long input.
 *
 * The limit starts at [firstMaxChars] and doubles with each chunk up to [maxChars]: playback can
 * start as soon as the short first chunk is ready, and each chunk is generated while the shorter
 * one before it plays.
 */
fun speechChunks(
    text: String,
    maxChars: Int = 300,
    firstMaxChars: Int = 60,
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
        for (sentence in sentences(paragraph, locale)) {
            for (piece in splitLong(sentence, limit())) {
                if (current.isNotEmpty() && current.length + 1 + piece.length > limit()) flush()
                if (current.isNotEmpty()) current.append(' ')
                current.append(piece)
            }
        }
        flush()
    }
    return chunks
}

private fun sentences(paragraph: String, locale: Locale): List<String> {
    val iterator = BreakIterator.getSentenceInstance(locale)
    iterator.setText(paragraph)
    val result = mutableListOf<String>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        paragraph.substring(start, end).trim().takeIf { it.isNotEmpty() }?.let(result::add)
        start = end
        end = iterator.next()
    }
    return result
}

private fun splitLong(sentence: String, maxChars: Int): List<String> {
    if (sentence.length <= maxChars) return listOf(sentence)
    val pieces = mutableListOf<String>()
    val current = StringBuilder()
    for (word in sentence.split(' ')) {
        if (current.isNotEmpty() && current.length + 1 + word.length > maxChars) {
            pieces += current.toString()
            current.clear()
        }
        if (current.isNotEmpty()) current.append(' ')
        current.append(word)
    }
    if (current.isNotEmpty()) pieces += current.toString()
    return pieces
}

/** Collapses the whitespace in extracted article text, keeping one line break between paragraphs. */
fun normalizeArticleText(text: String): String = text
    .replace('\u00A0', ' ')
    .split('\n')
    .map { it.replace(Regex("\\s+"), " ").trim() }
    .filter { it.isNotEmpty() }
    .joinToString("\n")
