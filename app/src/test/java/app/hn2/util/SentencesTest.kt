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
    fun `splits an overlong sentence between words`() {
        val chunks = speechChunks("aaaa bbbb cccc dddd", maxChars = 9, locale = Locale.US)
        assertEquals(listOf("aaaa bbbb", "cccc dddd"), chunks)
        assertTrue(chunks.all { it.length <= 9 })
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
