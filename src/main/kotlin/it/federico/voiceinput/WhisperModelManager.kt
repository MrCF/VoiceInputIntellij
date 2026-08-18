package it.federico.voiceinput

import com.intellij.openapi.progress.ProgressIndicator
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

object WhisperModelManager {

    data class ModelDefinition(
        val id: String,
        val modeName: String,
        val displayName: String,
        val fileName: String,
        val url: String,
        val approximateSize: String,
        val sha256: String? = null
    ) {
        override fun toString(): String {
            return "$modeName — $displayName"
        }
    }

    val models =
        listOf(
            ModelDefinition(
                id = "small",
                modeName = "Fast",
                displayName = "Small",
                fileName = "ggml-small.bin",
                url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin?download=true",
                approximateSize = "~466 MiB",
                sha256 =
                    "1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b"
            ),

            ModelDefinition(
                id = "medium",
                modeName = "Balanced",
                displayName = "Medium",
                fileName = "ggml-medium.bin",
                url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-medium.bin?download=true",
                approximateSize = "~1.4 GiB"
            ),

            ModelDefinition(
                id = "large-v3-turbo",
                modeName = "Quality",
                displayName = "Large v3 Turbo",
                fileName = "ggml-large-v3-turbo.bin",
                url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo.bin?download=true",
                approximateSize = "~1.5 GiB"
            )
        )

    private val modelDirectory: File
        get() = File(
            System.getProperty("user.home"),
            ".local/share/voice-input/models"
        )

    fun getModelDefinition(
        id: String
    ): ModelDefinition {

        return models.firstOrNull {
            it.id == id
        } ?: models.first()
    }

    fun getSelectedModelDefinition():
            ModelDefinition {

        val id =
            VoiceSettings
                .getInstance()
                .state
                .modelId

        return getModelDefinition(id)
    }

    fun getModelFile(
        model: ModelDefinition
    ): File {

        return File(
            modelDirectory,
            model.fileName
        )
    }

    fun isInstalled(
        model: ModelDefinition
    ): Boolean {

        val file =
            getModelFile(model)

        return file.isFile &&
                file.length() > 0
    }

    fun getSelectedModel(): File {

        return getModel(
            getSelectedModelDefinition()
        )
    }

    fun getModel(
        model: ModelDefinition
    ): File {

        val file =
            getModelFile(model)

        if (!isInstalled(model)) {

            throw IllegalStateException(
                "${model.displayName} model is not installed."
            )
        }

        return file
    }

    fun download(
        model: ModelDefinition,
        indicator: ProgressIndicator
    ) {

        if (!modelDirectory.exists()) {

            if (!modelDirectory.mkdirs()) {
                throw IllegalStateException(
                    "Unable to create model directory:\n" +
                            modelDirectory.absolutePath
                )
            }
        }

        val destination =
            getModelFile(model)

        val temporaryFile =
            File(
                modelDirectory,
                "${model.fileName}.download"
            )

        try {

            indicator.text =
                "Downloading Whisper ${model.displayName}"

            downloadFile(
                model,
                temporaryFile,
                indicator
            )

            indicator.checkCanceled()

            if (model.sha256 != null) {

                indicator.isIndeterminate =
                    true

                indicator.text =
                    "Verifying Whisper ${model.displayName}"

                indicator.text2 =
                    "Checking SHA-256"

                if (
                    !verifySha256(
                        temporaryFile,
                        model.sha256,
                        indicator
                    )
                ) {

                    throw IllegalStateException(
                        "Downloaded Whisper model failed SHA-256 verification."
                    )
                }
            }

            indicator.checkCanceled()

            Files.move(
                temporaryFile.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )

        } finally {

            if (temporaryFile.exists()) {
                temporaryFile.delete()
            }
        }
    }

    fun delete(
        model: ModelDefinition
    ) {

        val file =
            getModelFile(model)

        if (
            file.exists() &&
            !file.delete()
        ) {

            throw IllegalStateException(
                "Unable to remove Whisper model:\n" +
                        file.absolutePath
            )
        }
    }

    private fun downloadFile(
        model: ModelDefinition,
        destination: File,
        indicator: ProgressIndicator
    ) {

        val connection =
            URI(model.url)
                .toURL()
                .openConnection() as HttpURLConnection

        connection.instanceFollowRedirects =
            true

        connection.connectTimeout =
            15_000

        connection.readTimeout =
            30_000

        connection.setRequestProperty(
            "User-Agent",
            "VoiceInput-IntelliJ"
        )

        connection.connect()

        val responseCode =
            connection.responseCode

        if (responseCode !in 200..299) {

            connection.disconnect()

            throw IllegalStateException(
                "Unable to download Whisper model. HTTP $responseCode"
            )
        }

        val total =
            connection.contentLengthLong

        indicator.isIndeterminate =
            total <= 0

        try {

            connection.inputStream
                .buffered()
                .use { input ->

                    destination
                        .outputStream()
                        .buffered()
                        .use { output ->

                            val buffer =
                                ByteArray(
                                    1024 * 1024
                                )

                            var downloaded =
                                0L

                            while (true) {

                                indicator.checkCanceled()

                                val count =
                                    input.read(buffer)

                                if (count < 0) {
                                    break
                                }

                                output.write(
                                    buffer,
                                    0,
                                    count
                                )

                                downloaded += count

                                if (total > 0) {

                                    indicator.fraction =
                                        (
                                                downloaded.toDouble() /
                                                        total.toDouble()
                                                )
                                            .coerceIn(
                                                0.0,
                                                1.0
                                            )
                                }

                                indicator.text2 =
                                    if (total > 0) {

                                        "${formatSize(downloaded)} / " +
                                                formatSize(total)

                                    } else {

                                        formatSize(downloaded)
                                    }
                            }
                        }
                }

        } finally {

            connection.disconnect()
        }
    }

    private fun verifySha256(
        file: File,
        expected: String,
        indicator: ProgressIndicator
    ): Boolean {

        val digest =
            MessageDigest.getInstance(
                "SHA-256"
            )

        val total =
            file.length()

        var processed =
            0L

        file.inputStream()
            .buffered()
            .use { input ->

                val buffer =
                    ByteArray(
                        1024 * 1024
                    )

                while (true) {

                    indicator.checkCanceled()

                    val count =
                        input.read(buffer)

                    if (count < 0) {
                        break
                    }

                    digest.update(
                        buffer,
                        0,
                        count
                    )

                    processed += count

                    indicator.text2 =
                        "Verified ${formatSize(processed)} / ${formatSize(total)}"
                }
            }

        val calculated =
            digest.digest()
                .joinToString("") {
                    "%02x".format(it)
                }

        return calculated.equals(
            expected,
            ignoreCase = true
        )
    }

    private fun formatSize(
        bytes: Long
    ): String {

        val mib =
            bytes / 1024.0 / 1024.0

        return if (mib < 1024) {

            "%.1f MiB".format(mib)

        } else {

            "%.2f GiB".format(
                mib / 1024.0
            )
        }
    }
}
