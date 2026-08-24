package it.federico.voiceinput

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.TargetDataLine
import kotlin.math.sqrt

class AudioRecorder {

    companion object {

        private const val SAMPLE_RATE =
            16_000f

        private const val SAMPLE_SIZE_BITS =
            16

        private const val CHANNELS =
            1

        /*
         * Soglia minima assoluta.
         */
        private const val MIN_SILENCE_RMS_THRESHOLD =
            0.006

        /*
         * Soglia dinamica:
         *
         * noiseFloor * NOISE_MULTIPLIER
         */
        private const val NOISE_MULTIPLIER =
            2.8

        /*
         * Velocità di adattamento
         * al rumore ambientale.
         */
        private const val NOISE_FLOOR_ALPHA =
            0.08

        private const val INITIAL_NOISE_FLOOR =
            0.004

        /*
         * Prima di considerare rilevata
         * la voce richiediamo almeno
         * 200 ms di segnale.
         */
        private const val MIN_VOICE_DURATION_MS =
            200L

        /*
         * Circa 100 ms di audio:
         *
         * 16000 sample/sec
         * 2 byte/sample
         */
        private const val BUFFER_SIZE =
            3200
    }

    private val format =
        AudioFormat(
            SAMPLE_RATE,
            SAMPLE_SIZE_BITS,
            CHANNELS,
            true,
            false
        )

    @Volatile
    private var line:
            TargetDataLine? =
        null

    @Volatile
    private var recordingThread:
            Thread? =
        null

    @Volatile
    private var recording =
        false

    @Volatile
    private var stopRequested =
        false

    val outputFile =
        File(
            System.getProperty(
                "java.io.tmpdir"
            ),
            "voice-input.wav"
        )

    val isRecording: Boolean
        get() =
            recording

    /**
     * @param autoStopAllowed
     *
     * true:
     *   normale registrazione Meta+V.
     *   L'Automatic Stop può intervenire.
     *
     * false:
     *   modalità Push-To-Talk.
     *   La registrazione termina solamente
     *   quando viene richiesto esplicitamente
     *   lo stop.
     */
    @Synchronized
    fun start(
        autoStopAllowed: Boolean = true,
        onAutoStop: (() -> Unit)? = null
    ) {

        if (recording) {
            return
        }

        val currentLine =
            AudioInputManager.openLine(
                format,
                VoiceSettings.getInstance().state.inputDeviceId
            )

        currentLine.open(
            format
        )

        val settings =
            VoiceSettings
                .getInstance()
                .state

        /*
         * La linea di ingresso e' aperta ma non ancora avviata: il cue
         * conferma che il microfono e' pronto senza finire nel WAV.
         */
        if (settings.recordingAudioFeedbackEnabled) {
            RecordingAudioFeedback
                .playRecordingStarted()
        }

        currentLine.start()

        line =
            currentLine

        stopRequested =
            false

        recording =
            true

        /*
         * Punto fondamentale:
         *
         * il PTT passa autoStopAllowed = false.
         */
        val autoStopEnabled =
            autoStopAllowed &&
                    settings.autoStopEnabled

        val autoStopSilenceNanos =
            (
                    settings.autoStopSilenceSeconds *
                            1_000_000_000.0
                    ).toLong()

        recordingThread =
            Thread {

                recordLoop(
                    currentLine =
                        currentLine,

                    autoStopEnabled =
                        autoStopEnabled,

                    autoStopSilenceNanos =
                        autoStopSilenceNanos,

                    audioFeedbackEnabled =
                        settings.recordingAudioFeedbackEnabled,

                    onAutoStop =
                        onAutoStop
                )

            }.apply {

                name =
                    "VoiceInput-AudioRecorder"

                isDaemon =
                    true

                start()
            }
    }

    private fun recordLoop(
        currentLine: TargetDataLine,
        autoStopEnabled: Boolean,
        autoStopSilenceNanos: Long,
        audioFeedbackEnabled: Boolean,
        onAutoStop: (() -> Unit)?
    ) {

        val rawAudio =
            ByteArrayOutputStream()

        val buffer =
            ByteArray(
                BUFFER_SIZE
            )

        var speechDetected =
            false

        var accumulatedVoiceMs =
            0L

        var lastVoiceTime =
            System.nanoTime()

        var automaticStop =
            false

        /*
         * Stima adattiva del rumore ambientale.
         */
        var noiseFloor =
            INITIAL_NOISE_FLOOR

        var silenceThreshold =
            maxOf(
                MIN_SILENCE_RMS_THRESHOLD,
                noiseFloor *
                        NOISE_MULTIPLIER
            )

        try {

            while (
                !stopRequested &&
                currentLine.isOpen
            ) {

                val bytesRead =
                    try {

                        currentLine.read(
                            buffer,
                            0,
                            buffer.size
                        )

                    } catch (ex: Exception) {

                        if (stopRequested) {
                            break
                        }

                        throw ex
                    }

                if (bytesRead <= 0) {
                    continue
                }

                /*
                 * Tutto l'audio viene comunque
                 * scritto nel WAV.
                 */
                rawAudio.write(
                    buffer,
                    0,
                    bytesRead
                )

                /*
                 * In PTT arriviamo sempre qui
                 * perché autoStopEnabled = false.
                 */
                if (!autoStopEnabled) {
                    continue
                }

                val rms =
                    calculateRms(
                        buffer,
                        bytesRead
                    )

                val now =
                    System.nanoTime()

                val chunkDurationMs =
                    calculateChunkDurationMs(
                        bytesRead
                    )

                val isVoice =
                    rms >=
                            silenceThreshold

                if (isVoice) {

                    accumulatedVoiceMs +=
                        chunkDurationMs

                    lastVoiceTime =
                        now

                    if (
                        accumulatedVoiceMs >=
                        MIN_VOICE_DURATION_MS
                    ) {

                        speechDetected =
                            true
                    }

                } else {

                    /*
                     * Aggiornamento lento del
                     * rumore ambientale.
                     */
                    noiseFloor =
                        (
                                noiseFloor *
                                        (
                                                1.0 -
                                                        NOISE_FLOOR_ALPHA
                                                )
                                ) +
                                (
                                        rms *
                                                NOISE_FLOOR_ALPHA
                                        )

                    silenceThreshold =
                        maxOf(
                            MIN_SILENCE_RMS_THRESHOLD,
                            noiseFloor *
                                    NOISE_MULTIPLIER
                        )

                    /*
                     * Non fermiamo la registrazione
                     * prima che l'utente abbia parlato.
                     */
                    if (!speechDetected) {
                        continue
                    }

                    val silenceDuration =
                        now -
                                lastVoiceTime

                    if (
                        silenceDuration >=
                        autoStopSilenceNanos
                    ) {

                        automaticStop =
                            true

                        break
                    }
                }
            }

        } finally {

            try {

                if (currentLine.isRunning) {
                    currentLine.stop()
                }

            } catch (_: Exception) {
            }

            try {

                if (currentLine.isOpen) {
                    currentLine.close()
                }

            } catch (_: Exception) {
            }

            if (audioFeedbackEnabled) {
                RecordingAudioFeedback
                    .playRecordingStopped()
            }

            /*
             * Creiamo il WAV completo.
             */
            writeWaveFile(
                rawAudio.toByteArray()
            )

            synchronized(this) {

                if (
                    line ===
                    currentLine
                ) {

                    line =
                        null

                    recordingThread =
                        null

                    recording =
                        false
                }
            }

            /*
             * Callback soltanto quando
             * è stato davvero l'Automatic Stop.
             *
             * In PTT non può verificarsi.
             */
            if (
                automaticStop &&
                !stopRequested
            ) {

                onAutoStop
                    ?.invoke()
            }
        }
    }

    /**
     * Stop esplicito.
     *
     * Viene utilizzato sia dalla seconda
     * pressione di Meta+V sia dal rilascio
     * del pulsante PTT.
     */
    fun stop() {

        val currentLine:
                TargetDataLine?

        val currentThread:
                Thread?

        synchronized(this) {

            if (!recording) {
                return
            }

            stopRequested =
                true

            currentLine =
                line

            currentThread =
                recordingThread
        }

        try {

            currentLine
                ?.stop()

        } catch (_: Exception) {
        }

        try {

            currentLine
                ?.close()

        } catch (_: Exception) {
        }

        if (
            currentThread != null &&
            currentThread !==
            Thread.currentThread()
        ) {

            try {

                currentThread.join(
                    2000
                )

            } catch (_: InterruptedException) {

                Thread
                    .currentThread()
                    .interrupt()
            }
        }

        synchronized(this) {

            recording =
                false

            line =
                null

            recordingThread =
                null
        }
    }

    private fun calculateRms(
        buffer: ByteArray,
        length: Int
    ): Double {

        if (length < 2) {
            return 0.0
        }

        var sumSquares =
            0.0

        var samples =
            0

        var index =
            0

        while (
            index + 1 <
            length
        ) {

            val low =
                buffer[index]
                    .toInt() and
                        0xFF

            val high =
                buffer[index + 1]
                    .toInt()

            val sample =
                (
                        high shl 8
                        ) or low

            val normalized =
                sample /
                        32768.0

            sumSquares +=
                normalized *
                        normalized

            samples++

            index +=
                2
        }

        if (samples == 0) {
            return 0.0
        }

        return sqrt(
            sumSquares /
                    samples
        )
    }

    private fun calculateChunkDurationMs(
        bytesRead: Int
    ): Long {

        val bytesPerFrame =
            format.frameSize

        val frames =
            bytesRead.toDouble() /
                    bytesPerFrame

        return (
                frames /
                        format.frameRate *
                        1000.0
                ).toLong()
    }

    private fun writeWaveFile(
        audioBytes: ByteArray
    ) {

        if (audioBytes.isEmpty()) {
            return
        }

        val bytesPerFrame =
            format.frameSize

        val frameLength =
            audioBytes.size.toLong() /
                    bytesPerFrame

        ByteArrayInputStream(
            audioBytes
        ).use { byteStream ->

            AudioInputStream(
                byteStream,
                format,
                frameLength
            ).use { audioStream ->

                AudioSystem.write(
                    audioStream,
                    AudioFileFormat.Type.WAVE,
                    outputFile
                )
            }
        }
    }
}
