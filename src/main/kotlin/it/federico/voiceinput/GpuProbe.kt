package it.federico.voiceinput

import java.io.IOException

enum class ProcessingMode(private val label: String) {
    AUTOMATIC("Automatic (recommended)"), CPU_ONLY("CPU only"), GPU("GPU");

    override fun toString(): String = label
}

data class GpuAvailability(val available: Boolean, val message: String, val checked: Boolean = true) {
    companion object {
        val CHECKING = GpuAvailability(false, "Checking GPU availability… CPU processing remains available.", false)
    }
}

internal object GpuProbe {
    fun detect(run: () -> WhisperProcessResult): GpuAvailability = try {
        interpret(run())
    } catch (error: IOException) {
        if (error.message?.contains("timed out") == true) GpuAvailability(false, error.message!!)
        else GpuAvailability(false, "GPU availability could not be checked. CPU processing is available. Click Check again to retry.")
    }

    fun interpret(result: WhisperProcessResult): GpuAvailability {
        val device = result.stdout.lineSequence().firstOrNull { it.startsWith("GPU\t") }
            ?.substringAfter('\t')?.trim()?.take(200)
        return when {
            result.exitCode == 0 && !device.isNullOrEmpty() ->
                GpuAvailability(true, "GPU available: $device. Automatic mode can use GPU acceleration.")

            result.stderr.contains("libvulkan", ignoreCase = true) ->
                GpuAvailability(false, "GPU processing requires the Vulkan runtime libraries and a compatible GPU driver. Install them to enable GPU processing. CPU processing is available.")

            result.exitCode == 3 && result.stdout.trim() == "NO_GPU" ->
                GpuAvailability(false, "No compatible Vulkan GPU was found. Install a compatible Vulkan driver to enable GPU processing. CPU processing is available.")

            result.stderr.contains("error while loading shared libraries") ->
                GpuAvailability(false, "GPU runtime dependencies are missing or incompatible. Install the Vulkan runtime libraries and their dependencies to enable GPU processing. CPU processing is available.")

            else -> GpuAvailability(false, "The Vulkan GPU could not be initialized. Check the Vulkan libraries and GPU driver, then click Check again. CPU processing is available.")
        }
    }
}
