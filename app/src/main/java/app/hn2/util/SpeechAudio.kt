package app.hn2.util

import java.io.DataOutputStream
import java.io.File
import java.io.OutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Kokoro has a sizzly top end that phone speakers make scratchy, and it comes out about
 * 4 dB quieter than other voices. This turns everything above about 6 kHz down by 6 dB, then
 * boosts the whole thing by 6 dB. The boost is fixed rather than normalized per chunk, so the volume
 * doesn't jump from sentence to sentence; Kokoro peaks well under half of full scale, so rare
 * peaks above it are simply clamped.
 */
fun softenKokoro(samples: FloatArray, sampleRate: Int): FloatArray {
    val out = highShelf(samples, sampleRate, freq = 6000.0, gainDb = -6.0)
    for (i in out.indices) out[i] = (out[i] * 2f).coerceIn(-1f, 1f)
    return out
}

/** A high-shelf biquad from the RBJ Audio EQ Cookbook, with shelf slope 1. */
internal fun highShelf(samples: FloatArray, sampleRate: Int, freq: Double, gainDb: Double): FloatArray {
    val a = 10.0.pow(gainDb / 40)
    val w0 = 2 * PI * freq / sampleRate
    val cosW = cos(w0)
    val sa = 2 * sqrt(a) * sin(w0) / 2 * sqrt(2.0)
    val a0 = (a + 1) - (a - 1) * cosW + sa
    val b0 = a * ((a + 1) + (a - 1) * cosW + sa) / a0
    val b1 = -2 * a * ((a - 1) + (a + 1) * cosW) / a0
    val b2 = a * ((a + 1) + (a - 1) * cosW - sa) / a0
    val a1 = 2 * ((a - 1) - (a + 1) * cosW) / a0
    val a2 = ((a + 1) - (a - 1) * cosW - sa) / a0
    var x1 = 0.0
    var x2 = 0.0
    var y1 = 0.0
    var y2 = 0.0
    return FloatArray(samples.size) { i ->
        val x = samples[i].toDouble()
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = x
        y2 = y1
        y1 = y
        y.toFloat()
    }
}

/** Writes mono float samples (-1 to 1) as a 16-bit PCM WAV file. */
fun writeWav(file: File, samples: FloatArray, sampleRate: Int) = file.outputStream().buffered().use { writeWav(it, samples, sampleRate) }

internal fun writeWav(stream: OutputStream, samples: FloatArray, sampleRate: Int) {
    val data = samples.size * 2
    DataOutputStream(stream).apply {
        writeBytes("RIFF")
        writeLe32(36 + data)
        writeBytes("WAVEfmt ")
        writeLe32(16) // fmt chunk size
        writeLe16(1) // PCM
        writeLe16(1) // mono
        writeLe32(sampleRate)
        writeLe32(sampleRate * 2) // bytes per second
        writeLe16(2) // bytes per sample frame
        writeLe16(16) // bits per sample
        writeBytes("data")
        writeLe32(data)
        for (sample in samples) writeLe16((sample.coerceIn(-1f, 1f) * 32767).toInt())
        flush()
    }
}

private fun DataOutputStream.writeLe16(value: Int) {
    write(value and 0xff)
    write((value shr 8) and 0xff)
}

private fun DataOutputStream.writeLe32(value: Int) {
    writeLe16(value and 0xffff)
    writeLe16((value shr 16) and 0xffff)
}
