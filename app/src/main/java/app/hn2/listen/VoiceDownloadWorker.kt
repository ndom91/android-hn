package app.hn2.listen

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.hn2.http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * Downloads and unpacks one [VoiceModel]. It runs in WorkManager so it carries on when you leave
 * the settings screen. Progress (0 to 1) is reported as [KEY_PROGRESS].
 */
class VoiceDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val model = VoiceModel.fromName(inputData.getString(KEY_MODEL)) ?: return Result.failure()
        val staging = File(VoiceModel.voicesDir(applicationContext), ".staging-${model.archive}")
        return try {
            download(model, staging)
            Result.success()
        } catch (e: IOException) {
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Download failed")))
        } finally {
            staging.deleteRecursively()
        }
    }

    /**
     * Unpacks the archive as it arrives. Unpacking bzip2 takes a minute or two on a phone for Kokoro,
     * so doing it after the download left the progress bar stuck near the end.
     */
    private suspend fun download(model: VoiceModel, staging: File) = withContext(Dispatchers.IO) {
        staging.deleteRecursively()
        staging.mkdirs()
        val root = staging.canonicalFile
        // No cache: a 300 MB response would push everything else out of the app's HTTP cache.
        val client = applicationContext.http.newBuilder().cache(null).build()
        client.newCall(Request.Builder().url(model.url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed (HTTP ${response.code})")
            val body = response.body ?: throw IOException("Download failed")
            val total = body.contentLength().takeIf { it > 0 } ?: (model.sizeMb * 1_000_000L)
            val received = CountingInputStream(body.byteStream())
            var reported = -1
            suspend fun report() {
                val percent = (received.count * 100 / total).toInt().coerceAtMost(99)
                if (percent != reported) {
                    reported = percent
                    setProgress(workDataOf(KEY_PROGRESS to percent / 100f))
                }
            }
            TarArchiveInputStream(BZip2CompressorInputStream(received.buffered())).use { tar ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val entry = tar.nextEntry ?: break
                    val file = File(staging, entry.name).canonicalFile
                    // Never write outside the staging folder, whatever the archive says.
                    if (!file.path.startsWith(root.path + File.separator)) throw IOException("Bad file in download: ${entry.name}")
                    if (entry.isDirectory) {
                        file.mkdirs()
                        continue
                    }
                    file.parentFile?.mkdirs()
                    file.outputStream().use { output ->
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = tar.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            report()
                        }
                    }
                }
            }
        }
        val unpacked = File(staging, model.archive)
        if (!unpacked.isDirectory) throw IOException("The download didn't contain ${model.archive}")
        File(unpacked, VoiceModel.INSTALLED_MARKER).createNewFile()
        val target = model.dir(applicationContext)
        target.deleteRecursively()
        if (!unpacked.renameTo(target)) throw IOException("Couldn't save the voice")
        setProgress(workDataOf(KEY_PROGRESS to 1f))
    }

    companion object {
        const val KEY_MODEL = "model"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"

        fun workName(model: VoiceModel) = "voice-download-${model.name}"

        fun start(context: Context, model: VoiceModel) {
            val request = OneTimeWorkRequestBuilder<VoiceDownloadWorker>()
                .setInputData(workDataOf(KEY_MODEL to model.name))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(workName(model), ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context, model: VoiceModel) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(model))
        }
    }
}

/** Counts the bytes read through it, to report download progress from inside the unpacking. */
private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
    @Volatile var count = 0L
        private set

    override fun read(): Int = super.read().also { if (it >= 0) count++ }

    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }
}
