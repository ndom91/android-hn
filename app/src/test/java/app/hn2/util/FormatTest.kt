package app.hn2.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {

    private val now = 1_000_000_000L * 1000

    @Test
    fun `relative time`() {
        assertEquals("now", relativeTime(1_000_000_000L - 30, now))
        assertEquals("5m", relativeTime(1_000_000_000L - 300, now))
        assertEquals("12h", relativeTime(1_000_000_000L - 12 * 3600, now))
        assertEquals("3d", relativeTime(1_000_000_000L - 3 * 86400, now))
    }

    @Test
    fun `domain strips www`() {
        assertEquals("krebsonsecurity.com", domainOf("https://www.krebsonsecurity.com/2026/11/x/"))
        assertNull(domainOf(null))
    }

    @Test
    fun `hot thresholds match the widget screenshots`() {
        // Top stories: 373p and 367c are hot, 287p and 205c are not.
        assertTrue(isHotScore(373, 100))
        assertFalse(isHotScore(287, 100))
        assertTrue(isHotComments(367, 100))
        assertTrue(isHotComments(205, 100))
        assertFalse(isHotComments(95, 100))
    }
}
