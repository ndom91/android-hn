package app.hn2.listen

import android.content.Context
import app.hn2.data.VoiceEngine
import app.hn2.data.VoiceSettings
import java.io.Closeable
import java.io.File

/**
 * Turns text into a WAV file. Every engine writes files, so one player handles playback, the
 * notification and skipping for all of them.
 */
interface SpeechEngine : Closeable {
    /** Names the engine and voice, so cached audio from another voice isn't reused. */
    val voiceKey: String

    /** Writes [text] as speech to [out]. Writes nothing to [out] if it fails. */
    suspend fun synthesize(text: String, out: File)
}

/** A voice to pick in settings. [id] is what [AppSettings.setVoice][app.hn2.data.AppSettings.setVoice] stores. */
data class VoiceOption(val id: String, val label: String)

suspend fun createSpeechEngine(context: Context, settings: VoiceSettings): SpeechEngine = when (settings.engine) {
    VoiceEngine.SYSTEM -> SystemSpeechEngine.create(context, settings.voice)
    VoiceEngine.PIPER, VoiceEngine.KOKORO -> SherpaSpeechEngine.create(context, settings.engine, settings.voice)
}

suspend fun voiceOptions(context: Context, engine: VoiceEngine): List<VoiceOption> = when (engine) {
    VoiceEngine.SYSTEM -> SystemSpeechEngine.voices(context)
    VoiceEngine.PIPER, VoiceEngine.KOKORO -> SherpaSpeechEngine.voices(context, engine)
}
