package app.hn2.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockTest {

    @Test
    fun `blocks listed hosts and their subdomains`() {
        assertTrue(isAdHost("doubleclick.net"))
        assertTrue(isAdHost("securepubads.g.doubleclick.net"))
        assertTrue(isAdHost("CDN.Taboola.com"))
        assertTrue(isAdHost("connect.facebook.net"))
    }

    @Test
    fun `leaves other hosts alone`() {
        assertFalse(isAdHost("example.com"))
        assertFalse(isAdHost("facebook.net"))
        assertFalse(isAdHost("notdoubleclick.net"))
        assertFalse(isAdHost("google.com"))
        assertFalse(isAdHost(""))
    }
}
