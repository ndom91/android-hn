package app.hn2.listen

import android.content.Context
import app.hn2.data.VoiceEngine
import java.io.File

/**
 * Downloadable on-device voices, from sherpa-onnx's model releases. Each unpacks into its own
 * folder under `files/voices/`, named after the archive.
 */
enum class VoiceModel(val engine: VoiceEngine, val archive: String, val label: String, val sizeMb: Int) {
    PIPER_LESSAC(VoiceEngine.PIPER, "vits-piper-en_US-lessac-medium-int8", "Lessac · US English", 21),
    PIPER_AMY(VoiceEngine.PIPER, "vits-piper-en_US-amy-medium-int8", "Amy · US English", 21),
    PIPER_RYAN(VoiceEngine.PIPER, "vits-piper-en_US-ryan-medium-int8", "Ryan · US English", 21),
    // Full precision, not int8: on ARM, ONNX Runtime runs it about twice as fast with 4 threads, and
    // the int8 model sounds metallic.
    KOKORO(VoiceEngine.KOKORO, "kokoro-multi-lang-v1_0", "Kokoro · 28 English voices", 333);

    val url get() = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$archive.tar.bz2"

    fun dir(context: Context) = File(voicesDir(context), archive)

    /** Written last when unpacking, so a half-finished download never counts as installed. */
    fun isInstalled(context: Context) = File(dir(context), INSTALLED_MARKER).exists()

    fun delete(context: Context) = dir(context).deleteRecursively()

    companion object {
        const val INSTALLED_MARKER = ".installed"

        fun voicesDir(context: Context) = File(context.filesDir, "voices")

        fun fromName(name: String?): VoiceModel? = entries.firstOrNull { it.name == name }

        /** Downloads of voices the app no longer uses, deleted at startup. */
        val RETIRED_ARCHIVES = listOf("kokoro-int8-multi-lang-v1_0")
    }
}

/**
 * Kokoro v1.0's English speakers, by speaker id. The prefix says the accent and voice:
 * a = American, b = British; f = female, m = male. Ids 20 and up are British.
 */
val KOKORO_ENGLISH_SPEAKERS = listOf(
    "af_alloy", "af_aoede", "af_bella", "af_heart", "af_jessica", "af_kore", "af_nicole", "af_nova",
    "af_river", "af_sarah", "af_sky", "am_adam", "am_echo", "am_eric", "am_fenrir", "am_liam",
    "am_michael", "am_onyx", "am_puck", "am_santa", "bf_alice", "bf_emma", "bf_isabella", "bf_lily",
    "bm_daniel", "bm_fable", "bm_george", "bm_lewis",
)

/** "af_heart" → "Heart · US, female". */
fun kokoroSpeakerLabel(name: String): String {
    val accent = if (name.startsWith("b")) "UK" else "US"
    val gender = if (name.getOrNull(1) == 'm') "male" else "female"
    val first = name.substringAfter('_').replaceFirstChar { it.uppercase() }
    return "$first · $accent, $gender"
}
