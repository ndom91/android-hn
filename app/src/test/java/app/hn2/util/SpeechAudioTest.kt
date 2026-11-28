package app.hn2.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class SpeechAudioTest {

    private val rate = 24000

    private fun sine(freq: Double) = FloatArray(rate) { (0.25 * sin(2 * PI * freq * it / rate)).toFloat() }

    // Skips the first 1000 samples, while the filter settles.
    private fun rmsDb(samples: FloatArray) =
        20 * log10(sqrt(samples.drop(1000).sumOf { it.toDouble() * it } / (samples.size - 1000)))

    @Test
    fun `high shelf leaves speech frequencies and cuts the top by 6 dB`() {
        val low = sine(500.0)
        val high = sine(10_000.0)
        assertEquals(0.0, rmsDb(highShelf(low, rate, 6000.0, -6.0)) - rmsDb(low), 0.3)
        assertEquals(-6.0, rmsDb(highShelf(high, rate, 6000.0, -6.0)) - rmsDb(high), 0.5)
    }

    @Test
    fun `softening boosts speech by 6 dB and clamps`() {
        val low = sine(500.0)
        assertEquals(6.0, rmsDb(softenKokoro(low, rate)) - rmsDb(low), 0.3)
        val loud = FloatArray(10) { 0.9f }
        assertEquals(1f, softenKokoro(loud, rate).max(), 0f)
    }

    @Test
    fun `writes a 16-bit mono WAV`() {
        val out = ByteArrayOutputStream()
        writeWav(out, floatArrayOf(0f, 1f, -1f), 22050)
        val bytes = ByteBuffer.wrap(out.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(44 + 6, bytes.limit())
        assertArrayEquals("RIFF".toByteArray(), out.toByteArray().copyOfRange(0, 4))
        assertEquals(1, bytes.getShort(22).toInt()) // channels
        assertEquals(22050, bytes.getInt(24))
        assertEquals(16, bytes.getShort(34).toInt())
        assertEquals(6, bytes.getInt(40))
        assertEquals(32767, bytes.getShort(46).toInt())
        assertEquals(-32767, bytes.getShort(48).toInt())
    }
}
