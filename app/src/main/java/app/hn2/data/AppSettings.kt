package app.hn2.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which text-to-speech engine reads articles aloud. */
enum class VoiceEngine(val label: String) {
    SYSTEM("System"),
    PIPER("Piper (medium)"),
    KOKORO("Kokoro (large)");

    companion object {
        fun fromName(name: String?): VoiceEngine = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** The chosen engine, and the chosen voice for each engine (null means the engine's default). */
data class VoiceSettings(val engine: VoiceEngine, val voices: Map<VoiceEngine, String>) {
    val voice: String? get() = voices[engine]
}

/** App settings, kept in the `settings` SharedPreferences. */
class AppSettings(private val prefs: SharedPreferences) {

    private val _voice = MutableStateFlow(loadVoice())
    val voice: StateFlow<VoiceSettings> = _voice.asStateFlow()

    fun setVoiceEngine(engine: VoiceEngine) {
        prefs.edit().putString(KEY_ENGINE, engine.name).apply()
        _voice.value = loadVoice()
    }

    /** [voice] is an engine-specific voice id, or null for the engine's default. */
    fun setVoice(engine: VoiceEngine, voice: String?) {
        prefs.edit().putString(KEY_VOICE + engine.name, voice).apply()
        _voice.value = loadVoice()
    }

    private fun loadVoice() = VoiceSettings(
        engine = VoiceEngine.fromName(prefs.getString(KEY_ENGINE, null)),
        voices = VoiceEngine.entries.mapNotNull { engine ->
            prefs.getString(KEY_VOICE + engine.name, null)?.let { engine to it }
        }.toMap(),
    )

    private companion object {
        const val KEY_ENGINE = "voice_engine"
        const val KEY_VOICE = "voice_"
    }
}
