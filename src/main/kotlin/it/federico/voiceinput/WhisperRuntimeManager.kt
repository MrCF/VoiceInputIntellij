package it.federico.voiceinput

import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

enum class WhisperBackend { VULKAN, CPU }

data class WhisperRuntime(val directory: File, val backend: WhisperBackend) {
    val executable: File get() = File(directory, "whisper-cli")
}

/** Separate, content-versioned directories prevent mixing incompatible native libraries on upgrade. */
internal class WhisperRuntimeBundle(
    private val root: File,
    manifest: String,
    private val resource: (String) -> InputStream
) {
    private val revision = MessageDigest.getInstance("SHA-256")
        .digest(manifest.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
    private val paths = manifest.lineSequence().filter { it.isNotBlank() }
        .map { it.substringAfter("  ") }.toList()

    @Synchronized
    fun install(backend: WhisperBackend): WhisperRuntime {
        val cpu = backend == WhisperBackend.CPU
        val files = paths.filter { it.startsWith("cpu/") == cpu }
        check(files.any { it.substringAfterLast('/') == "whisper-cli" }) { "Incomplete Whisper runtime manifest" }
        val directory = File(root, "$revision/${backend.name.lowercase()}")
        val runtime = WhisperRuntime(directory, backend)
        val marker = File(directory, ".complete")
        fun installed() = marker.isFile && runtime.executable.canExecute() &&
                files.all {
                    val file = File(directory, it.substringAfterLast('/'))
                    file.length() > 0 && (file.name !in listOf("whisper-cli", "gpu-probe") || file.canExecute())
                }
        if (installed()) return runtime

        Files.createDirectories(directory.toPath())
        // Multiple IDE processes may share this cache. Publish completion only after every copy succeeds.
        FileChannel.open(
            File(directory, ".install.lock").toPath(),
            StandardOpenOption.CREATE, StandardOpenOption.WRITE
        ).use { channel ->
            channel.lock().use {
                if (!installed()) {
                    Files.deleteIfExists(marker.toPath())
                    files.forEach { path ->
                        resource(path).use { input ->
                            Files.copy(
                                input, File(directory, path.substringAfterLast('/')).toPath(),
                                StandardCopyOption.REPLACE_EXISTING
                            )
                        }
                    }
                    files.map { File(directory, it.substringAfterLast('/')) }
                        .filter { it.name in listOf("whisper-cli", "gpu-probe") }
                        .forEach { check(it.setExecutable(true)) { "Cannot make Whisper executable: $it" } }
                    marker.writeText(revision)
                }
            }
        }
        return runtime
    }
}

object WhisperRuntimeManager {
    private const val RESOURCE_ROOT = "/runtime/linux-x64"
    private val logger = Logger.getInstance(WhisperRuntimeManager::class.java)

    private fun resource(path: String): InputStream =
        checkNotNull(javaClass.getResourceAsStream("$RESOURCE_ROOT/$path")) {
            "Missing Whisper runtime resource: $path"
        }

    private val bundle by lazy {
        WhisperRuntimeBundle(
            File(System.getProperty("user.home"), ".local/share/voice-input/runtime/linux-x64"),
            resource("SHA256SUMS").bufferedReader().use { it.readText() },
            ::resource
        )
    }

    private val executor: WhisperRuntimeExecutor by lazy {
        WhisperRuntimeExecutor(bundle::install, onCpuFallback = {
            GpuAvailabilityService.getInstance().transcriptionFailed()
            logger.info(
                "Whisper switched to the CPU runtime after the accelerated runtime failed; " +
                        "CPU will be used until a successful GPU recheck or IDE restart."
            )
        })
    }

    internal fun probeGpu(): GpuAvailability = GpuProbe.detect {
        WhisperNativeProcess.probe(bundle.install(WhisperBackend.VULKAN))
    }

    internal fun resetCpuFallback(): Unit = executor.resetCpuFallback()

    fun transcribe(arguments: List<String>, mode: ProcessingMode): String {
        if (mode == ProcessingMode.CPU_ONLY) return executor.execute(arguments, mode)
        val availability = GpuAvailabilityService.getInstance().check().get()
        if (mode == ProcessingMode.GPU && !availability.available) {
            throw java.io.IOException("${availability.message} Select Automatic or CPU only to continue.")
        }
        return executor.execute(arguments, if (availability.available) mode else ProcessingMode.CPU_ONLY)
    }
}
