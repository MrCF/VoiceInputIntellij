package it.federico.voiceinput

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

/** Coalesces concurrent requests and caches results until an explicit recheck. */
internal class GpuAvailabilityMonitor(
    private val executor: ExecutorService,
    private val probe: () -> GpuAvailability,
    private val onAvailable: () -> Unit = {}
) : AutoCloseable {
    @Volatile
    var current = GpuAvailability.CHECKING
        private set
    private var pending: CompletableFuture<GpuAvailability>? = null
    private var task: Future<*>? = null
    private var closed = false
    private val listeners = CopyOnWriteArrayList<(GpuAvailability) -> Unit>()

    fun addListener(listener: (GpuAvailability) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (GpuAvailability) -> Unit) {
        listeners.remove(listener)
    }

    private fun publish(value: GpuAvailability) {
        current = value
        listeners.forEach { it(value) }
    }

    @Synchronized
    fun check(force: Boolean = false): CompletableFuture<GpuAvailability> {
        check(!closed) { "GPU availability monitor is disposed" }
        pending?.let { return it }
        if (!force && current.checked) return CompletableFuture.completedFuture(current)
        val result = CompletableFuture<GpuAvailability>()
        pending = result
        publish(GpuAvailability.CHECKING)
        task = executor.submit {
            try {
                val detected = probe()
                synchronized(this) {
                    if (!closed) {
                        if (detected.available) onAvailable()
                        publish(detected)
                        pending = null
                        result.complete(detected)
                    }
                }
            } catch (error: Exception) {
                synchronized(this) {
                    if (!closed) {
                        publish(GpuAvailability(false, "GPU availability could not be checked. CPU processing is available. Click Check again to retry."))
                        pending = null
                        if (error is InterruptedException) result.cancel(false) else result.complete(current)
                    }
                }
            }
        }
        return result
    }

    @Synchronized
    fun transcriptionFailed() {
        if (!closed) publish(
            GpuAvailability(
                false,
                "GPU transcription failed. Automatic mode is using CPU. Click Check again to retry GPU acceleration."
            )
        )
    }

    @Synchronized
    override fun close() {
        closed = true
        task?.cancel(true)
        pending?.cancel(false)
        listeners.clear()
    }
}

@Service(Service.Level.APP)
class GpuAvailabilityService : Disposable {
    private val monitor: GpuAvailabilityMonitor = GpuAvailabilityMonitor(
        AppExecutorUtil.getAppExecutorService(), WhisperRuntimeManager::probeGpu, WhisperRuntimeManager::resetCpuFallback
    )
    val current: GpuAvailability get() = monitor.current
    fun check(force: Boolean = false): CompletableFuture<GpuAvailability> = monitor.check(force)
    fun addListener(listener: (GpuAvailability) -> Unit) = monitor.addListener(listener)
    fun removeListener(listener: (GpuAvailability) -> Unit) = monitor.removeListener(listener)
    fun transcriptionFailed(): Unit = monitor.transcriptionFailed()
    override fun dispose() = monitor.close()

    companion object {
        fun getInstance(): GpuAvailabilityService = ApplicationManager.getApplication().getService(GpuAvailabilityService::class.java)
    }
}

class GpuAvailabilityStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (VoiceSettings.getInstance().state.processingMode != ProcessingMode.CPU_ONLY) {
            GpuAvailabilityService.getInstance().check()
        }
    }
}
