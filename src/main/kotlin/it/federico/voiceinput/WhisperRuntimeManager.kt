package it.federico.voiceinput

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object WhisperRuntimeManager {

    private const val RESOURCE_ROOT =
        "/runtime/linux-x64"

    private val runtimeDir: File
        get() = File(
            System.getProperty("user.home"),
            ".local/share/voice-input/runtime/linux-x64"
        )

    private val runtimeFiles = listOf(
        "whisper-cli",
        "libwhisper.so.1",
        "libggml.so.0",
        "libggml-base.so.0",
        "libggml-cpu.so.0",
        "libggml-vulkan.so.0"
    )

    fun getRuntimeDirectory(): File {
        ensureInstalled()
        return runtimeDir
    }

    fun getExecutable(): File {
        ensureInstalled()

        return File(
            runtimeDir,
            "whisper-cli"
        )
    }

    fun isInstalled(): Boolean {

        return runtimeFiles.all { fileName ->
            File(runtimeDir, fileName).isFile
        } &&
                File(
                    runtimeDir,
                    "whisper-cli"
                ).canExecute()
    }

    @Synchronized
    fun ensureInstalled() {

        if (isInstalled()) {
            return
        }

        install()
    }

    private fun install() {

        if (!runtimeDir.exists()) {

            if (!runtimeDir.mkdirs()) {
                throw IllegalStateException(
                    "Impossibile creare la directory runtime:\n" +
                            runtimeDir.absolutePath
                )
            }
        }

        runtimeFiles.forEach { fileName ->
            extractResource(fileName)
        }

        val executable =
            File(runtimeDir, "whisper-cli")

        if (!executable.setExecutable(true)) {
            throw IllegalStateException(
                "Impossibile rendere eseguibile whisper-cli:\n" +
                        executable.absolutePath
            )
        }

        if (!isInstalled()) {
            throw IllegalStateException(
                "Installazione del runtime Whisper non riuscita."
            )
        }
    }

    private fun extractResource(fileName: String) {

        val resourcePath =
            "$RESOURCE_ROOT/$fileName"

        val inputStream =
            WhisperRuntimeManager::class.java
                .getResourceAsStream(resourcePath)
                ?: throw IllegalStateException(
                    "Risorsa runtime non trovata: $resourcePath"
                )

        val destination =
            File(runtimeDir, fileName)

        inputStream.use { input ->

            Files.copy(
                input,
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }
}
