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
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * Downloads and unpacks one [VoiceModel]. It runs in WorkManager so it carries on when you leave
 * the settings screen. Progress (0 to 1) is reported as [KEY_PROGRESS].
 */
class VoiceDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val model = VoiceModel.fromName(inputData.getString(KEY_MODEL)) ?: return Result.failure()
        return try {
            download(model)
            Result.success()
        } catch (e: IOException) {
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Download failed")))
        } finally {
            File(VoiceModel.voicesDir(applicationContext), "${model.archive}.tar.bz2.part").delete()
        }
    }

    private suspend fun download(model: VoiceModel) = withContext(Dispatchers.IO) {
        val voices = VoiceModel.voicesDir(applicationContext).apply { mkdirs() }
        val archive = File(voices, "${model.archive}.tar.bz2.part")
        // No cache: a 130 MB response would push everything else out of the app's HTTP cache.
        val client = applicationContext.http.newBuilder().cache(null).build()
        client.newCall(Request.Builder().url(model.url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed (HTTP ${response.code})")
            val body = response.body ?: throw IOException("Download failed")
            val total = body.contentLength().takeIf { it > 0 } ?: (model.sizeMb * 1_000_000L)
            body.byteStream().use { input ->
                archive.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var reported = -1
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        // Most of the time goes to the download; unpacking is the last 10%.
                        val percent = (copied * 90 / total).toInt()
                        if (percent != reported) {
                            reported = percent
                            setProgress(workDataOf(KEY_PROGRESS to percent / 100f))
                        }
                    }
                }
            }
        }
        unpack(archive, voices, model)
    }

    private suspend fun unpack(archive: File, voices: File, model: VoiceModel) {
        val target = model.dir(applicationContext)
        target.deleteRecursively()
        val root = voices.canonicalFile
        TarArchiveInputStream(BZip2CompressorInputStream(archive.inputStream().buffered())).use { tar ->
            while (true) {
                coroutineContext.ensureActive()
                val entry = tar.nextEntry ?: break
                val file = File(voices, entry.name).canonicalFile
                // Never write outside the voices folder, whatever the archive says.
                if (!file.path.startsWith(root.path + File.separator)) throw IOException("Bad file in download: ${entry.name}")
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    file.outputStream().use { tar.copyTo(it) }
                }
            }
        }
        if (!target.isDirectory) throw IOException("The download didn't contain ${model.archive}")
        File(target, VoiceModel.INSTALLED_MARKER).createNewFile()
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
