package app.hn2.listen

import android.content.Context
import app.hn2.data.VoiceEngine
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import app.hn2.util.softenKokoro
import app.hn2.util.writeWav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Piper and Kokoro voices, run on the phone by sherpa-onnx from a downloaded [VoiceModel]. */
class SherpaSpeechEngine private constructor(
    private val tts: OfflineTts,
    private val speakerId: Int,
    override val voiceKey: String,
    /** Kokoro only: Piper has almost nothing above 8 kHz and peaks too high to boost. */
    private val soften: Boolean = false,
) : SpeechEngine {

    override suspend fun synthesize(text: String, out: File) = withContext(Dispatchers.Default) {
        val audio = tts.generate(text, speakerId, 1f)
        val partial = File(out.path + ".tmp")
        try {
            if (audio.samples.isEmpty()) throw IOException("Couldn't generate speech")
            val samples = if (soften) softenKokoro(audio.samples, audio.sampleRate) else audio.samples
            writeWav(partial, samples, audio.sampleRate)
            if (!partial.renameTo(out)) throw IOException("Couldn't save speech")
        } finally {
            partial.delete()
        }
    }

    override fun close() = tts.release()

    companion object {
        /** [voice] is a [VoiceModel] name for Piper, or a Kokoro speaker id. Null picks the first available. */
        suspend fun create(context: Context, engine: VoiceEngine, voice: String?): SherpaSpeechEngine =
            withContext(Dispatchers.IO) {
                when (engine) {
                    VoiceEngine.PIPER -> piper(context, voice)
                    VoiceEngine.KOKORO -> kokoro(context, voice)
                    VoiceEngine.SYSTEM -> error("Not a sherpa-onnx engine")
                }
            }

        fun voices(context: Context, engine: VoiceEngine): List<VoiceOption> = when (engine) {
            VoiceEngine.PIPER -> VoiceModel.entries
                .filter { it.engine == VoiceEngine.PIPER && it.isInstalled(context) }
                .map { VoiceOption(it.name, it.label) }
            VoiceEngine.KOKORO -> if (VoiceModel.KOKORO.isInstalled(context)) {
                KOKORO_ENGLISH_SPEAKERS.mapIndexed { id, name -> VoiceOption(id.toString(), kokoroSpeakerLabel(name)) }
            } else {
                emptyList()
            }
            VoiceEngine.SYSTEM -> emptyList()
        }

        private fun piper(context: Context, voice: String?): SherpaSpeechEngine {
            val installed = VoiceModel.entries.filter { it.engine == VoiceEngine.PIPER && it.isInstalled(context) }
            val model = installed.firstOrNull { it.name == voice } ?: installed.firstOrNull()
                ?: throw IOException("Download a Piper voice in Settings › Voice first")
            val dir = model.dir(context)
            val onnx = dir.listFiles().orEmpty().firstOrNull { it.extension == "onnx" }
                ?: throw IOException("The ${model.label} download is incomplete; download it again")
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = onnx.path,
                        tokens = File(dir, "tokens.txt").path,
                        dataDir = File(dir, "espeak-ng-data").path,
                    ),
                    numThreads = threads(),
                ),
            )
            return SherpaSpeechEngine(OfflineTts(config = config), 0, "piper:${model.name}")
        }

        private fun kokoro(context: Context, voice: String?): SherpaSpeechEngine {
            val model = VoiceModel.KOKORO
            if (!model.isInstalled(context)) throw IOException("Download Kokoro in Settings › Voice first")
            val speaker = voice?.toIntOrNull()?.takeIf { it in KOKORO_ENGLISH_SPEAKERS.indices } ?: DEFAULT_KOKORO_SPEAKER
            val dir = model.dir(context)
            val british = KOKORO_ENGLISH_SPEAKERS[speaker].startsWith("b")
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    kokoro = OfflineTtsKokoroModelConfig(
                        model = File(dir, "model.onnx").path,
                        voices = File(dir, "voices.bin").path,
                        tokens = File(dir, "tokens.txt").path,
                        dataDir = File(dir, "espeak-ng-data").path,
                        lexicon = File(dir, if (british) "lexicon-gb-en.txt" else "lexicon-us-en.txt").path,
                    ),
                    numThreads = threads(),
                ),
            )
            return SherpaSpeechEngine(OfflineTts(config = config), speaker, "${model.archive}:$speaker", soften = true)
        }

        // af_heart, Kokoro's best-rated English voice.
        private const val DEFAULT_KOKORO_SPEAKER = 3

        // Half the cores: fast enough to stay ahead of playback without starving the UI.
        private fun threads() = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)
    }
}
