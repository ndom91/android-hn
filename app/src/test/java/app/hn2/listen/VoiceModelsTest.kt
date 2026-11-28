package app.hn2.listen

import org.junit.Assert.assertEquals
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
}
