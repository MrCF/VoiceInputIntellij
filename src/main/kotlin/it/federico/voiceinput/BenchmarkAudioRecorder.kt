package it.federico.voiceinput

import java.io.File
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.TargetDataLine

class BenchmarkAudioRecorder {

    private var line:
            TargetDataLine? =
        null

    private var recordingThread:
            Thread? =
        null

    private var startedAt:
            Long =
        0L

    val outputFile =
        File(
            System.getProperty(
                "java.io.tmpdir"
            ),
            "voice-input-benchmark.wav"
        )

    val isRecording: Boolean
        get() =
            line?.isOpen == true

    fun start() {

        if (isRecording) {
            return
        }

        val format =
            AudioFormat(
                16000f,
                16,
                1,
                true,
                false
            )

        line =
            AudioInputManager.openLine(
                format,
                VoiceSettings.getInstance().state.inputDeviceId
            )

        line!!.open(format)
        line!!.start()

        startedAt =
            System.nanoTime()

        recordingThread =
            Thread {

                AudioInputStream(
                    line
                ).use { stream ->

                    AudioSystem.write(
                        stream,
                        AudioFileFormat.Type.WAVE,
                        outputFile
                    )
                }

            }.apply {

                name =
                    "VoiceInput-BenchmarkRecorder"

                start()
            }
    }

    fun stop(): Double {

        val currentLine =
            line ?: return 0.0

        val elapsed =
            (
                    System.nanoTime() -
                            startedAt
                    ) /
                    1_000_000_000.0

        currentLine.stop()
        currentLine.close()

        recordingThread
            ?.join(2000)

        line = null
        recordingThread = null

        return elapsed
    }
}
