package app.hn2.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class CoroutinesTest {

    @Test
    fun `captures ordinary failures`() {
        val result = runSuspendCatching { throw IOException("offline") }
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals(2, runSuspendCatching { 2 }.getOrNull())
    }

    @Test(expected = CancellationException::class)
    fun `rethrows cancellation`() {
        runSuspendCatching { throw CancellationException("cancelled") }
    }
}
