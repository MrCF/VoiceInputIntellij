package it.federico.voiceinput

import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal data class WhisperProcessResult(val exitCode: Int, val stdout: String, val stderr: String)

/** The accelerated attempt has no editor/history side effects; only a successful result is returned. */
internal class WhisperRuntimeExecutor(
    private val runtime: (WhisperBackend) -> WhisperRuntime,
    private val run: (WhisperRuntime, List<String>) -> WhisperProcessResult = WhisperNativeProcess::run,
    private val onCpuFallback: () -> Unit = {}
) {
    @Volatile
    private var cpuOnly = false

    fun resetCpuFallback() {
        cpuOnly = false
    }

    fun execute(arguments: List<String>, mode: ProcessingMode = ProcessingMode.AUTOMATIC): String {
        if (mode == ProcessingMode.GPU) return attempt(WhisperBackend.VULKAN, arguments)
        if (mode == ProcessingMode.CPU_ONLY || cpuOnly) return attempt(WhisperBackend.CPU, arguments)
        val acceleratedFailure = try {
            return attempt(WhisperBackend.VULKAN, arguments)
        } catch (error: IOException) {
            error
        }
        // Do not retry cancellations or programming/configuration errors.
        try {
            val result = attempt(WhisperBackend.CPU, arguments)
            cpuOnly = true
            onCpuFallback()
            return result
        } catch (error: IOException) {
            error.addSuppressed(acceleratedFailure)
            throw error
        }
    }

    private fun attempt(backend: WhisperBackend, arguments: List<String>): String {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Whisper transcription cancelled")
        val result = run(runtime(backend), arguments)
        if (result.exitCode != 0) {
            throw IOException("Whisper $backend terminated with exit code ${result.exitCode}\n${result.stderr}")
        }
        return result.stdout
    }
}

internal object WhisperNativeProcess {
    fun run(runtime: WhisperRuntime, arguments: List<String>): WhisperProcessResult =
        execute(runtime, arguments, runtime.executable.absolutePath, null)

    fun probe(runtime: WhisperRuntime, timeoutSeconds: Long = 10): WhisperProcessResult =
        execute(runtime, emptyList(), java.io.File(runtime.directory, "gpu-probe").absolutePath, timeoutSeconds)

    private fun execute(runtime: WhisperRuntime, arguments: List<String>, executable: String, timeoutSeconds: Long?): WhisperProcessResult {
        val command = listOf(executable) + arguments +
                if (runtime.backend == WhisperBackend.CPU) listOf("--no-gpu") else emptyList()
        val builder = ProcessBuilder(command).directory(runtime.directory)
        // Search only this bundle first; CPU and Vulkan GGML libraries must never be mixed.
        val environment = builder.environment()
        environment["LD_LIBRARY_PATH"] = runtime.directory.absolutePath +
                environment["LD_LIBRARY_PATH"]?.takeIf { it.isNotBlank() }?.let { ":$it" }.orEmpty()
        // GGML dynamic CPU backends are loaded from the executable directory.
        environment.remove("GGML_BACKEND_PATH")

        val stderr = Files.createTempFile("voice-input-whisper-", ".stderr")
        try {
            // Redirect stderr so a full pipe cannot deadlock stdout consumption.
            val process = builder.redirectError(stderr.toFile()).start()
            Executors.newVirtualThreadPerTaskExecutor().use { reader ->
                val output = reader.submit(Callable {
                    process.inputStream.bufferedReader().use { it.readText() }
                })
                try {
                    if (timeoutSeconds != null && !process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                        throw IOException("GPU availability check timed out. CPU processing is available. Click Check again to retry.")
                    }
                    val exitCode = process.waitFor()
                    val stdout = try {
                        output.get()
                    } catch (error: ExecutionException) {
                        throw IOException("Unable to read Whisper output", error.cause)
                    }
                    return WhisperProcessResult(exitCode, stdout, Files.readString(stderr))
                } finally {
                    if (process.isAlive) process.destroyForcibly()
                    output.cancel(true)
                }
            }
        } finally {
            Files.deleteIfExists(stderr)
        }
    }
}
