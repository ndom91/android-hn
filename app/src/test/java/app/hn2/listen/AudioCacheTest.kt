package app.hn2.listen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class AudioCacheTest {

    @Test
    fun `deletes only files older than the limit`() {
        val dir = Files.createTempDirectory("listen").toFile()
        val now = 10 * AudioCache.MAX_AGE_MS
        val old = dir.resolve("old.wav").apply { writeText("x"); setLastModified(now - AudioCache.MAX_AGE_MS - 1000) }
        val fresh = dir.resolve("fresh.wav").apply { writeText("x"); setLastModified(now - 1000) }

        assertEquals(1, AudioCache.deleteOld(dir, now))
        assertFalse(old.exists())
        assertTrue(fresh.exists())
        dir.deleteRecursively()
    }

    @Test
    fun `file names depend on voice and text`() {
        val dir = Files.createTempDirectory("listen").toFile()
        val a = AudioCache.fileFor(dir, "system:a", "Hello.")
        assertEquals(a, AudioCache.fileFor(dir, "system:a", "Hello."))
        assertNotEquals(a, AudioCache.fileFor(dir, "system:b", "Hello."))
        assertNotEquals(a, AudioCache.fileFor(dir, "system:a", "Bye."))
        dir.deleteRecursively()
    }
}
