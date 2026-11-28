package app.hn2.util

import java.text.BreakIterator
import java.util.Locale

/**
 * Splits text into chunks for text-to-speech: whole sentences, merged up to [maxChars] so each
 * chunk is a natural pause point. Paragraphs never share a chunk. A sentence longer than [maxChars]
 * is split between words, since engines slow down or refuse very long input.
 */
fun speechChunks(text: String, maxChars: Int = 300, locale: Locale = Locale.getDefault()): List<String> {
    val chunks = mutableListOf<String>()
    for (paragraph in text.split('\n')) {
        val current = StringBuilder()
        fun flush() {
            if (current.isNotBlank()) chunks += current.toString().trim()
            current.clear()
        }
        for (sentence in sentences(paragraph, locale)) {
            for (piece in splitLong(sentence, maxChars)) {
                if (current.isNotEmpty() && current.length + 1 + piece.length > maxChars) flush()
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
