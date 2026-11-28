package app.hn2.listen

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Generated speech, one WAV file per chunk. Files are named by a hash of the voice and the text,
 * so listening to the same story with the same voice again reuses them instead of regenerating.
 */
object AudioCache {

    /** How long unused audio is kept. Using a file again resets its age. */
    val MAX_AGE_MS = TimeUnit.DAYS.toMillis(3)

    fun dir(context: Context): File = File(context.cacheDir, "listen").apply { mkdirs() }

    fun fileFor(dir: File, voiceKey: String, text: String): File {
        val digest = MessageDigest.getInstance("SHA-1").digest("$voiceKey\n$text".toByteArray())
        return File(dir, digest.joinToString("") { "%02x".format(it) } + ".wav")
    }

    /** Deletes files (including unfinished `.tmp` ones) last used more than [maxAgeMs] ago. Returns how many. */
    fun deleteOld(dir: File, now: Long = System.currentTimeMillis(), maxAgeMs: Long = MAX_AGE_MS): Int =
        dir.listFiles().orEmpty().count { it.isFile && now - it.lastModified() > maxAgeMs && it.delete() }
}
