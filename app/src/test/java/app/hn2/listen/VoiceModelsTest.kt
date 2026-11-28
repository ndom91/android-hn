package app.hn2.listen

import app.hn2.data.VoiceEngine
import app.hn2.data.VoiceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceModelsTest {

    @Test
    fun `labels Kokoro speakers by accent and voice`() {
        assertEquals("Heart · US, female", kokoroSpeakerLabel("af_heart"))
        assertEquals("George · UK, male", kokoroSpeakerLabel("bm_george"))
    }

    @Test
    fun `British Kokoro speakers start at id 20`() {
        assertEquals("bf_alice", KOKORO_ENGLISH_SPEAKERS[20])
        assertEquals(28, KOKORO_ENGLISH_SPEAKERS.size)
    }

    private fun settings(engine: VoiceEngine, voice: String? = null) =
        VoiceSettings(engine, if (voice != null) mapOf(engine to voice) else emptyMap())

    @Test
    fun `knows which download is in use`() {
        val installed = setOf(VoiceModel.PIPER_LESSAC, VoiceModel.PIPER_RYAN, VoiceModel.KOKORO)
        // No Piper voice chosen: the first downloaded one is used.
        assertTrue(settings(VoiceEngine.PIPER).isUsing(VoiceModel.PIPER_LESSAC, installed))
        assertFalse(settings(VoiceEngine.PIPER).isUsing(VoiceModel.PIPER_RYAN, installed))
        assertTrue(settings(VoiceEngine.PIPER, "PIPER_RYAN").isUsing(VoiceModel.PIPER_RYAN, installed))
        assertTrue(settings(VoiceEngine.KOKORO, "3").isUsing(VoiceModel.KOKORO, installed))
        assertFalse(settings(VoiceEngine.SYSTEM).isUsing(VoiceModel.KOKORO, installed))
    }

    @Test
    fun `falls back to the phone's voices when an engine loses its last download`() {
        val twoPiper = setOf(VoiceModel.PIPER_LESSAC, VoiceModel.PIPER_AMY)
        assertEquals(VoiceEngine.PIPER, fallbackAfterDeleting(VoiceModel.PIPER_LESSAC, twoPiper))
        assertEquals(VoiceEngine.SYSTEM, fallbackAfterDeleting(VoiceModel.PIPER_LESSAC, setOf(VoiceModel.PIPER_LESSAC)))
        assertEquals(VoiceEngine.SYSTEM, fallbackAfterDeleting(VoiceModel.KOKORO, setOf(VoiceModel.KOKORO)))
    }
}
