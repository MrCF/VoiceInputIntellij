package it.federico.voiceinput

import com.intellij.openapi.progress.ProgressIndicator
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object WhisperVadManager {

    private const val VAD_FILE_NAME =
        "ggml-silero-v6.2.0.bin"

    /*
     * URL del modello VAD usato da whisper.cpp.
     *
     * Lo teniamo separato dai normali modelli speech-to-text.
     */
    private const val VAD_URL =
        "https://huggingface.co/ggml-org/whisper-vad/resolve/main/ggml-silero-v6.2.0.bin"

    private val vadDirectory: File
        get() = File(
            System.getProperty("user.home"),
            ".local/share/voice-input/vad"
        )

    val vadFile: File
        get() = File(
            vadDirectory,
            VAD_FILE_NAME
        )

    fun isInstalled(): Boolean {
        return vadFile.isFile &&
                vadFile.length() > 0
    }

    fun getModel(): File {

        if (!isInstalled()) {
            throw IllegalStateException(
                "Voice Activity Detection model is not installed."
            )
        }

        return vadFile
    }

    fun download(
        indicator: ProgressIndicator
    ) {

        if (!vadDirectory.exists()) {

            if (!vadDirectory.mkdirs()) {
                throw IllegalStateException(
                    "Unable to create VAD directory:\n" +
                            vadDirectory.absolutePath
                )
            }
        }

        val temporaryFile =
            File(
                vadDirectory,
                "$VAD_FILE_NAME.download"
            )

        try {

            indicator.text =
                "Downloading Voice Activity Detection model"

            downloadFile(
                temporaryFile,
                indicator
            )

            indicator.checkCanceled()

            if (
                !temporaryFile.isFile ||
                temporaryFile.length() == 0L
            ) {
                throw IllegalStateException(
                    "Downloaded VAD model is empty or invalid."
                )
            }

            Files.move(
                temporaryFile.toPath(),
                vadFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )

        } finally {

            if (temporaryFile.exists()) {
                temporaryFile.delete()
            }
        }
    }

    fun delete() {

        if (
            vadFile.exists() &&
            !vadFile.delete()
        ) {
            throw IllegalStateException(
                "Unable to remove VAD model:\n" +
                        vadFile.absolutePath
            )
        }
    }

    private fun downloadFile(
        destination: File,
        indicator: ProgressIndicator
    ) {

        val connection =
            URI(VAD_URL)
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
                "Unable to download VAD model. HTTP $responseCode"
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

                                    indicator.text2 =
                                        "${formatSize(downloaded)} / " +
                                                formatSize(total)

                                } else {

                                    indicator.text2 =
                                        formatSize(downloaded)
                                }
                            }
                        }
                }

        } finally {

            connection.disconnect()
        }
    }

    private fun formatSize(
        bytes: Long
    ): String {

        val mib =
            bytes / 1024.0 / 1024.0

        return "%.1f MiB".format(
            mib
        )
    }
}
