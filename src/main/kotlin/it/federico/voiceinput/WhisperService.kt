package it.federico.voiceinput

import java.io.File

class WhisperService {

    fun transcribe(
        audioFile: File,
        prompt: String
    ): String {

        val settings =
            VoiceSettings.getInstance().state

        val model =
            WhisperModelManager
                .getSelectedModelDefinition()

        return transcribe(
            audioFile = audioFile,
            prompt = prompt,
            modelDefinition = model,
            language = settings.language,
            threads = settings.threads
        )
    }

    fun transcribe(
        audioFile: File,
        prompt: String,
        modelDefinition:
        WhisperModelManager.ModelDefinition,
        language: String,
        threads: Int,
        punctuationMode: PunctuationMode = VoiceSettings.getInstance().state.punctuationMode,
        processingMode: ProcessingMode = VoiceSettings.getInstance().state.processingMode
    ): String {

        val settings =
            VoiceSettings.getInstance().state

        val model =
            WhisperModelManager
                .getModel(
                    modelDefinition
                )

        val command =
            mutableListOf(
                "-m",
                model.absolutePath,

                "-f",
                audioFile.absolutePath,

                "-l",
                language,

                "-t",
                threads.toString(),

                "--no-timestamps",

                "--no-prints"
            )

        if (prompt.isNotBlank()) {
            command += listOf(
                "--prompt",
                prompt
            )
        }

        /*
         * Voice Activity Detection
         */
        if (settings.vadEnabled) {

            if (!WhisperVadManager.isInstalled()) {
                throw IllegalStateException(
                    "Voice Activity Detection is enabled, " +
                            "but the VAD model is not installed."
                )
            }

            val vadModel =
                WhisperVadManager.getModel()

            command += listOf(
                "--vad",

                "-vm",
                vadModel.absolutePath,

                "-vt",
                settings.vadThreshold.toString(),

                "-vspd",
                settings.vadMinSpeechDurationMs.toString(),

                "-vsd",
                settings.vadMinSilenceDurationMs.toString(),

                "-vp",
                settings.vadSpeechPadMs.toString(),

                "-vo",
                settings.vadSamplesOverlapSeconds.toString()
            )
        }

        val stdout = WhisperRuntimeManager.transcribe(command, processingMode)
        return TranscriptionText.format(stdout, punctuationMode)
    }
}
