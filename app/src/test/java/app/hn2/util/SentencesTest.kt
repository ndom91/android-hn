package app.hn2.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SentencesTest {

    @Test
    fun `merges short sentences up to the limit`() {
        val chunks = speechChunks("One. Two. Three.", maxChars = 9, locale = Locale.US)
        assertEquals(listOf("One. Two.", "Three."), chunks)
    }

    @Test
    fun `keeps paragraphs apart`() {
        assertEquals(listOf("First.", "Second."), speechChunks("First.\nSecond.", locale = Locale.US))
    }

    @Test
    fun `never cuts a sentence, even one longer than the limit`() {
        val long = "This opening sentence is a good deal longer than sixty characters, as most are."
        val chunks = speechChunks("$long Short one.", maxChars = 300, firstMaxChars = 60, locale = Locale.US)
        assertEquals(listOf(long, "Short one."), chunks)
    }

    @Test
    fun `every chunk ends at a sentence end`() {
        val text = (1..30).joinToString(" ") { "Sentence $it has, of course, a few more words than needed." }
        assertTrue(speechChunks(text, locale = Locale.US).all { it.endsWith(".") })
    }

    @Test
    fun `abbreviations and initials don't end a sentence`() {
        assertEquals(
            listOf("Mr. Smith met J. R. R. Tolkien, e.g. at the U.S. office.", "Then he left."),
            sentences("Mr. Smith met J. R. R. Tolkien, e.g. at the U.S. office. Then he left.", Locale.US),
        )
    }

    @Test
    fun `splits a run-on past the hard limit at a comma`() {
        val chunks = speechChunks("alpha beta, gamma delta, epsilon zeta", maxChars = 20, firstMaxChars = 20, hardMaxChars = 25, locale = Locale.US)
        assertEquals(listOf("alpha beta, gamma delta,", "epsilon zeta"), chunks)
    }

    @Test
    fun `normalizes whitespace and drops blank lines`() {
        assertEquals("a b\nc", normalizeArticleText("  a \t b \n\n  \n  c  "))
    }

    @Test
    fun `chunks start short and grow`() {
        val text = (1..40).joinToString(" ") { "Sentence number $it is here." }
        val sizes = speechChunks(text, maxChars = 300, firstMaxChars = 60, locale = Locale.US).map { it.length }
        assertTrue(sizes[0] <= 60)
        assertTrue(sizes[1] in 61..120)
        assertTrue(sizes[2] in 121..240)
        assertTrue(sizes.drop(3).dropLast(1).all { it in 241..300 })
    }
}
