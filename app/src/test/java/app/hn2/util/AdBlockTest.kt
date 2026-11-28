package app.hn2.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdBlockTest {

    private val hosts = setOf("doubleclick.net", "taboola.com", "connect.facebook.net")

    @Test
    fun `blocks listed hosts and their subdomains`() {
        assertTrue(isAdHost("doubleclick.net", hosts))
        assertTrue(isAdHost("securepubads.g.doubleclick.net", hosts))
        assertTrue(isAdHost("CDN.Taboola.com", hosts))
        assertTrue(isAdHost("connect.facebook.net", hosts))
    }

    @Test
    fun `leaves other hosts alone`() {
        assertFalse(isAdHost("example.com", hosts))
        assertFalse(isAdHost("facebook.net", hosts))
        assertFalse(isAdHost("notdoubleclick.net", hosts))
        assertFalse(isAdHost("google.com", hosts))
        assertFalse(isAdHost("", hosts))
    }

    @Test
    fun `bundled EasyList hosts are plain host names`() {
        // Gradle runs unit tests from the module directory.
        val lines = File("src/main/assets/adhosts.txt").readLines()
        assertTrue(lines.size > 10_000)
        assertTrue(lines.all { Regex("[a-z0-9.-]+").matches(it) })
    }
}
