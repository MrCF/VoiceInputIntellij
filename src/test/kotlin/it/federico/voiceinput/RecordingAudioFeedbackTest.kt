package it.federico.voiceinput

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingAudioFeedbackTest {

    @Test
    fun `tone is 80 ms mono 16 bit PCM with silent boundaries`() {

        val tone =
            RecordingAudioFeedback.createTone(
                startFrequency = 660.0,
                endFrequency = 880.0
            )

        assertEquals(
            44_100 * 80 / 1_000 * 2,
            tone.size
        )

        assertEquals(0, sampleAt(tone, 0))
        assertTrue(
            kotlin.math.abs(
                sampleAt(
                    tone,
                    tone.size / 4
                )
            ) > 100
        )
        assertTrue(
            kotlin.math.abs(
                sampleAt(
                    tone,
                    tone.size / 2 - 1
                )
            ) < 100
        )
    }

    private fun sampleAt(
        bytes: ByteArray,
        sampleIndex: Int
    ): Int {

        val byteIndex =
            sampleIndex * 2

        return (
                (bytes[byteIndex].toInt() and 0xff) or
                        (bytes[byteIndex + 1].toInt() shl 8)
                ).toShort().toInt()
    }
}
