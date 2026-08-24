package it.federico.voiceinput

import com.intellij.openapi.diagnostic.Logger
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.sin

object RecordingAudioFeedback {

    private val logger =
        Logger.getInstance(
            RecordingAudioFeedback::class.java
        )

    private const val SAMPLE_RATE =
        44_100f

    private const val DURATION_MS =
        80

    private const val VOLUME =
        0.16

    private val format =
        AudioFormat(
            SAMPLE_RATE,
            16,
            1,
            true,
            false
        )

    private val startTone =
        createTone(
            startFrequency = 660.0,
            endFrequency = 880.0
        )

    private val stopTone =
        createTone(
            startFrequency = 880.0,
            endFrequency = 550.0
        )

    /**
     * La riproduzione e' intenzionalmente sincrona: il chiamante avvia
     * l'acquisizione soltanto dopo la fine del cue.
     */
    fun playRecordingStarted() {
        play(startTone)
    }

    /**
     * Viene chiamato soltanto dopo la chiusura della linea di ingresso.
     */
    fun playRecordingStopped() {
        play(stopTone)
    }

    private fun play(
        samples: ByteArray
    ) {

        try {

            val outputLine =
                AudioSystem.getSourceDataLine(
                    format
                )

            outputLine.use {

                it.open(format)
                it.start()
                it.write(
                    samples,
                    0,
                    samples.size
                )
                it.drain()
            }

        } catch (ex: Exception) {
            /*
             * Il feedback e' accessorio: l'assenza di un dispositivo di
             * uscita non deve impedire registrazione o trascrizione.
             */
            logger.debug(
                "Unable to play recording audio feedback.",
                ex
            )
        }
    }

    internal fun createTone(
        startFrequency: Double,
        endFrequency: Double
    ): ByteArray {

        val sampleCount =
            (SAMPLE_RATE * DURATION_MS / 1_000)
                .toInt()

        val result =
            ByteArray(sampleCount * 2)

        var phase =
            0.0

        for (sampleIndex in 0 until sampleCount) {

            val progress =
                sampleIndex.toDouble() /
                        (sampleCount - 1)

            val frequency =
                startFrequency +
                        (endFrequency - startFrequency) *
                        progress

            phase +=
                2.0 * PI * frequency /
                        SAMPLE_RATE

            // Finestra sinusoidale: evita click all'inizio e alla fine.
            val envelope =
                sin(PI * progress)

            val sample =
                (sin(phase) *
                        envelope *
                        VOLUME *
                        Short.MAX_VALUE)
                    .toInt()
                    .toShort()

            val byteIndex =
                sampleIndex * 2

            result[byteIndex] =
                (sample.toInt() and 0xff)
                    .toByte()

            result[byteIndex + 1] =
                (sample.toInt() shr 8 and 0xff)
                    .toByte()
        }

        return result
    }
}
