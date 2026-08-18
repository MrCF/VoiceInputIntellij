package it.federico.voiceinput

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
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
         * Soglia minima assoluta: evita che in ambienti
         * quasi perfettamente silenziosi la soglia adattiva
         * diventi troppo sensibile a piccoli fruscii.
         */
        private const val MIN_SILENCE_RMS_THRESHOLD =
            0.006

        /*
         * La soglia voce viene calcolata come:
         *
         * noiseFloor * NOISE_MULTIPLIER
         *
         * con un minimo pari a MIN_SILENCE_RMS_THRESHOLD.
         */
        private const val NOISE_MULTIPLIER =
            2.8

        /*
         * Velocità con cui aggiorniamo il rumore di fondo
         * quando il chunk sembra silenzioso.
         *
         * Valore basso = adattamento lento e stabile.
         */
        private const val NOISE_FLOOR_ALPHA =
            0.08

        /*
         * Valore RMS iniziale prudente, usato finché
         * non abbiamo raccolto abbastanza audio.
         */
        private const val INITIAL_NOISE_FLOOR =
            0.004

        /*
         * Prima di considerare valida la voce vogliamo
         * almeno un po' di parlato reale.
         *
         * Evita che un piccolo rumore faccia partire
         * immediatamente il timer del silenzio.
         */
        private const val MIN_VOICE_DURATION_MS =
            200L

        /*
         * Leggiamo circa 100 ms di audio per volta.
         *
         * 16000 sample/s
         * 2 byte/sample
         * 100 ms = 3200 byte
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
    private var line: TargetDataLine? =
        null

    @Volatile
    private var recordingThread: Thread? =
        null

    @Volatile
    private var recording =
        false

    @Volatile
    private var stopRequested =
        false

    val outputFile =
        File(
            System.getProperty("java.io.tmpdir"),
            "voice-input.wav"
        )

    val isRecording: Boolean
        get() = recording

    /**
     * Avvia la registrazione.
     *
     * onAutoStop viene chiamato soltanto quando la
     * registrazione termina automaticamente per silenzio.
     *
     * Non viene chiamato quando l'utente preme nuovamente
     * Meta+V e quindi usa stop() manualmente.
     */
    @Synchronized
    fun start(
        onAutoStop: (() -> Unit)? = null
    ) {

        if (recording) {
            return
        }

        val info =
            DataLine.Info(
                TargetDataLine::class.java,
                format
            )

        val currentLine =
            AudioSystem.getLine(info)
                    as TargetDataLine

        currentLine.open(format)
        currentLine.start()

        line =
            currentLine

        stopRequested =
            false

        recording =
            true

        /*
         * Facciamo uno snapshot delle Settings.
         *
         * Se l'utente cambia le Settings durante una
         * registrazione, i nuovi valori saranno usati
         * dalla registrazione successiva.
         */
        val settings =
            VoiceSettings
                .getInstance()
                .state

        val autoStopEnabled =
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
         *
         * Parte da un valore prudente e viene aggiornata
         * lentamente solo sui chunk che sembrano silenziosi.
         */
        var noiseFloor =
            INITIAL_NOISE_FLOOR

        var silenceThreshold =
            maxOf(
                MIN_SILENCE_RMS_THRESHOLD,
                noiseFloor * NOISE_MULTIPLIER
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

                        /*
                         * Quando stop() chiude la linea,
                         * una read() in corso può terminare
                         * con un'eccezione.
                         *
                         * È normale durante lo stop manuale.
                         */
                        if (stopRequested) {
                            break
                        }

                        throw ex
                    }

                if (bytesRead <= 0) {
                    continue
                }

                rawAudio.write(
                    buffer,
                    0,
                    bytesRead
                )

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
                    rms >= silenceThreshold

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
                     * Aggiorniamo lentamente la stima del
                     * rumore di fondo solo quando il chunk
                     * sembra silenzioso.
                     *
                     * In questo modo il plugin si adatta
                     * gradualmente a ventole, rumore della
                     * stanza, microfoni più o meno sensibili,
                     * senza inseguire la voce dell'utente.
                     */
                    noiseFloor =
                        (
                                noiseFloor *
                                        (1.0 - NOISE_FLOOR_ALPHA)
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
                     * Prima che venga rilevata almeno
                     * un po' di voce non facciamo partire
                     * l'auto-stop.
                     *
                     * Puoi quindi premere Meta+V,
                     * aspettare qualche secondo e poi
                     * iniziare a parlare.
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

            /*
             * Scriviamo il WAV soltanto quando abbiamo
             * terminato di acquisire i campioni.
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
             * Il callback parte soltanto per lo stop
             * automatico.
             *
             * A questo punto voice-input.wav è già
             * completo e pronto per Whisper.
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
     * Stop manuale, usato dalla seconda pressione di Meta+V.
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

        /*
         * Non facciamo mai join sul thread stesso.
         */
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

    /**
     * Calcola RMS normalizzato 0..1
     * da PCM signed 16-bit little endian.
     */
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
