package it.federico.voiceinput

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSessionServiceTest {

    @Test
    fun `recording can overlap queued transcriptions`() {

        VoiceSessionService.setTranscribing()

        try {
            assertTrue(VoiceSessionService.isTranscribing)
            assertFalse(VoiceSessionService.isRecording)

            VoiceSessionService.setRecording()

            assertTrue(VoiceSessionService.isTranscribing)
            assertTrue(VoiceSessionService.isRecording)
            assertEquals(
                VoiceSessionService.State.RECORDING,
                VoiceSessionService.state
            )

            VoiceSessionService.setTranscribing()

            assertFalse(VoiceSessionService.isRecording)
            assertTrue(VoiceSessionService.isTranscribing)
            assertEquals(
                VoiceSessionService.State.TRANSCRIBING,
                VoiceSessionService.state
            )

            VoiceSessionService.transcriptionFinished()
            assertTrue(VoiceSessionService.isTranscribing)

            VoiceSessionService.transcriptionFinished()
            assertFalse(VoiceSessionService.isTranscribing)
            assertEquals(
                VoiceSessionService.State.IDLE,
                VoiceSessionService.state
            )
        } finally {
            VoiceSessionService.setIdle()
            while (VoiceSessionService.isTranscribing) {
                VoiceSessionService.transcriptionFinished()
            }
        }
    }
}
