package app.hn2.listen

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The phone's own text-to-speech engine (usually Google's), with its offline voices. */
class SystemSpeechEngine private constructor(private val tts: TextToSpeech) : SpeechEngine {

    private val pending = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    override val voiceKey = "system:${tts.defaultEngine}:${tts.voice?.name}"

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {}
            override fun onDone(utteranceId: String) {
                pending.remove(utteranceId)?.complete(Unit)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) = onError(utteranceId, TextToSpeech.ERROR)
            override fun onError(utteranceId: String, errorCode: Int) {
                pending.remove(utteranceId)?.completeExceptionally(IOException("Text-to-speech failed ($errorCode)"))
            }
        })
    }

    override suspend fun synthesize(text: String, out: File) {
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Unit>().also { pending[id] = it }
        // Written under another name and renamed when complete, so a half-written file is never cached.
        val partial = File(out.path + ".tmp")
        try {
            if (tts.synthesizeToFile(text, Bundle(), partial, id) != TextToSpeech.SUCCESS) {
                throw IOException("Text-to-speech refused the text")
            }
            done.await()
            if (!partial.renameTo(out)) throw IOException("Couldn't save speech")
        } finally {
            pending.remove(id)
            partial.delete()
        }
    }

    override fun close() = tts.shutdown()

    companion object {
        suspend fun create(context: Context, voiceName: String?): SystemSpeechEngine {
            val tts = connect(context)
            voiceName?.let { name -> tts.voices?.firstOrNull { it.name == name }?.let(tts::setVoice) }
            return SystemSpeechEngine(tts)
        }

        /** Offline, installed voices in the phone's language, best quality first. */
        suspend fun voices(context: Context): List<VoiceOption> {
            val tts = connect(context)
            try {
                val language = Locale.getDefault().language
                return tts.voices.orEmpty()
                    .filter { !it.isNetworkConnectionRequired && it.locale.language == language && !it.notInstalled() }
                    .sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.locale.toString() }.thenBy { it.name })
                    .map { VoiceOption(it.name, label(it)) }
            } finally {
                tts.shutdown()
            }
        }

        // Voice names look like "en-us-x-iol-local"; the part after "-x-" tells voices apart.
        private fun label(voice: Voice): String {
            val variant = voice.name.substringAfter("-x-", "").substringBefore('-').ifEmpty { voice.name }
            return "${voice.locale.displayName} · $variant"
        }

        private fun Voice.notInstalled() = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in features.orEmpty()

        private suspend fun connect(context: Context): TextToSpeech = suspendCancellableCoroutine { cont ->
            lateinit var tts: TextToSpeech
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    cont.resume(tts)
                } else {
                    tts.shutdown()
                    cont.resumeWithException(IOException("This phone has no text-to-speech engine"))
                }
            }
            cont.invokeOnCancellation { tts.shutdown() }
        }
    }
}
