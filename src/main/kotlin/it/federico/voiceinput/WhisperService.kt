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
        threads: Int
    ): String {

        val runtimeDirectory =
            WhisperRuntimeManager
                .getRuntimeDirectory()

        val whisperCli =
            WhisperRuntimeManager
                .getExecutable()

        val model =
            WhisperModelManager
                .getModel(
                    modelDefinition
                )

        val command =
            mutableListOf(
                whisperCli.absolutePath,

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

        val processBuilder =
            ProcessBuilder(command)

        val environment =
            processBuilder.environment()

        val existingLibraryPath =
            environment["LD_LIBRARY_PATH"]

        environment["LD_LIBRARY_PATH"] =
            if (
                existingLibraryPath
                    .isNullOrBlank()
            ) {

                runtimeDirectory.absolutePath

            } else {

                runtimeDirectory.absolutePath +
                        File.pathSeparator +
                        existingLibraryPath
            }

        val process =
            processBuilder.start()

        val stdout =
            process.inputStream
                .bufferedReader()
                .readText()

        val stderr =
            process.errorStream
                .bufferedReader()
                .readText()

        val exitCode =
            process.waitFor()

        if (exitCode != 0) {

            throw RuntimeException(
                "Whisper terminated with exit code " +
                        "$exitCode\n$stderr"
            )
        }

        return stdout.trim()
    }
}
