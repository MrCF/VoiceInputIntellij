package it.federico.voiceinput

import java.io.File
import javax.sound.sampled.*

class AudioRecorder {

    private var line: TargetDataLine? = null
    private var recordingThread: Thread? = null

    val outputFile = File("/tmp/voice-input.wav")

    val isRecording: Boolean
        get() = line?.isOpen == true

    fun start() {
        if (isRecording) {
            return
        }

        val format = AudioFormat(
            16000f,   // sample rate
            16,       // bit
            1,        // mono
            true,     // signed
            false     // little endian
        )

        val info = DataLine.Info(
            TargetDataLine::class.java,
            format
        )

        line = AudioSystem.getLine(info) as TargetDataLine
        line!!.open(format)
        line!!.start()

        recordingThread = Thread {
            AudioInputStream(line).use { audioStream ->

                AudioSystem.write(
                    audioStream,
                    AudioFileFormat.Type.WAVE,
                    outputFile
                )
            }
        }.apply {
            name = "VoiceInput-AudioRecorder"
            start()
        }
    }

    fun stop() {
        val currentLine = line ?: return

        currentLine.stop()
        currentLine.close()

        recordingThread?.join(2000)

        line = null
        recordingThread = null
    }
}
