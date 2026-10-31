package it.federico.voiceinput

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.TargetDataLine

class AudioRecorderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `empty hold after speech removes previous audio`() {
        val audioFile = temporaryFolder.newFile("recording.wav")
        var input = FakeInput(listOf(voiceChunk(), voiceChunk()))
        val recorder = AudioRecorder(audioFile, ::settings) { _, _ -> input.line }
        record(recorder, input)
        assertTrue(audioFile.exists())
        AudioSystem.getAudioInputStream(audioFile).use {
            assertEquals(3200L, it.frameLength)
        }

        input = FakeInput(emptyList())
        record(recorder, input)
        assertFalse(audioFile.exists())
    }

    @Test
    fun `immediate release never reuses a previous wav`() {
        val audioFile = temporaryFolder.newFile("recording.wav")
        val recorder = AudioRecorder(audioFile, ::settings) { _, _ -> FakeInput(emptyList()).line }

        repeat(20) {
            audioFile.writeBytes(byteArrayOf(1, 2, 3, 4))
            recorder.start(autoStopAllowed = false)
            recorder.stop()

            assertFalse(audioFile.exists())
            assertFalse(recorder.isRecording)
        }
    }

    @Test
    fun `speech after an empty hold creates fresh audio`() {
        val audioFile = temporaryFolder.newFile("recording.wav")
        var input = FakeInput(emptyList())
        val recorder = AudioRecorder(audioFile, ::settings) { _, _ -> input.line }
        record(recorder, input)
        assertFalse(audioFile.exists())

        input = FakeInput(List(3) { voiceChunk() })
        record(recorder, input)
        AudioSystem.getAudioInputStream(audioFile).use {
            assertEquals(4800L, it.frameLength)
        }
    }

    @Test
    fun `silent hold after speech removes previous audio`() {
        val audioFile = temporaryFolder.newFile("recording.wav")
        var input = FakeInput(listOf(voiceChunk(), voiceChunk()))
        val recorder = AudioRecorder(audioFile, ::settings) { _, _ -> input.line }
        record(recorder, input)
        assertTrue(audioFile.exists())

        input = FakeInput(List(10) { ByteArray(3200) })
        record(recorder, input)
        assertFalse(audioFile.exists())
    }

    @Test
    fun `brief noise does not count as speech`() {
        val audioFile = temporaryFolder.newFile("recording.wav")
        val input = FakeInput(listOf(voiceChunk(), ByteArray(3200)))
        val recorder = AudioRecorder(audioFile, ::settings) { _, _ -> input.line }
        record(recorder, input)
        assertFalse(audioFile.exists())
    }

    @Test
    fun `hold retains speech and trailing silence without automatic stop`() {
        val audioFile = temporaryFolder.newFile("recording.wav")
        val chunks = listOf(voiceChunk(), voiceChunk()) + List(10) { ByteArray(3200) }
        val input = FakeInput(chunks)
        val recorder = AudioRecorder(audioFile, ::settings) { _, _ -> input.line }
        record(recorder, input)
        AudioSystem.getAudioInputStream(audioFile).use {
            assertEquals(19200L, it.frameLength)
        }
    }

    @Test
    fun `toggle with automatic stop disabled also ignores silence`() {
        val audioFile = temporaryFolder.newFile("recording.wav")
        val input = FakeInput(List(10) { ByteArray(3200) })
        val recorder = AudioRecorder(audioFile, {
            settings().copy(autoStopEnabled = false)
        }) { _, _ -> input.line }
        record(recorder, input, autoStopAllowed = true)
        assertFalse(audioFile.exists())
    }

    private fun settings() = VoiceSettings.State(
        autoStopEnabled = true,
        autoStopSilenceSeconds = 0.0,
        recordingAudioFeedbackEnabled = false
    )

    private fun record(recorder: AudioRecorder, input: FakeInput, autoStopAllowed: Boolean = false) {
        var autoStopped = false
        recorder.start(autoStopAllowed) { autoStopped = true }
        try {
            assertTrue("Recorder must consume all supplied audio", input.consumed.await(5, TimeUnit.SECONDS))
            assertTrue(recorder.isRecording)
        } finally {
            recorder.stop()
        }
        assertFalse(autoStopped)
        assertFalse(recorder.isRecording)
    }

    private fun voiceChunk() = ByteArray(3200).apply {
        for (index in indices step 2) {
            this[index] = 0
            this[index + 1] = 16
        }
    }

    private class FakeInput(private val chunks: List<ByteArray>) {
        val consumed = CountDownLatch(1)
        private val closed = CountDownLatch(1)
        private var nextChunk = 0
        @Volatile
        private var open = false
        @Volatile
        private var running = false

        val line = Proxy.newProxyInstance(
            TargetDataLine::class.java.classLoader,
            arrayOf(TargetDataLine::class.java)
        ) { _, method, arguments ->
            when (method.name) {
                "open" -> {
                    open = true; null
                }

                "start" -> {
                    running = true; null
                }

                "stop" -> {
                    running = false; null
                }

                "close" -> {
                    open = false; closed.countDown(); null
                }

                "isOpen" -> open
                "isRunning" -> running
                "read" -> {
                    if (nextChunk < chunks.size) {
                        val chunk = chunks[nextChunk++]
                        chunk.copyInto(arguments!![0] as ByteArray, arguments[1] as Int)
                        chunk.size
                    } else {
                        consumed.countDown()
                        check(closed.await(5, TimeUnit.SECONDS))
                        0
                    }
                }

                else -> error("Unexpected audio line call: ${method.name}")
            }
        } as TargetDataLine
    }
}
