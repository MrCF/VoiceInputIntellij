package it.federico.voiceinput

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException

class WhisperRuntimeExecutorTest {
    private val arguments = listOf("-m", "/model", "-f", "/audio", "-t", "4", "--vad")
    private fun runtime(backend: WhisperBackend) = WhisperRuntime(File("/unused"), backend)

    @Test
    fun `working Vulkan does not run CPU`() {
        val calls = mutableListOf<WhisperBackend>()
        val executor = WhisperRuntimeExecutor(::runtime, { runtime, args ->
            calls += runtime.backend
            assertEquals(arguments, args)
            WhisperProcessResult(0, "recognized", "")
        })
        assertEquals("recognized", executor.execute(arguments))
        assertEquals(listOf(WhisperBackend.VULKAN), calls)
    }

    @Test
    fun `missing loader falls back once and remembers CPU for the session`() {
        val calls = mutableListOf<WhisperBackend>()
        var notices = 0
        val executor = WhisperRuntimeExecutor(::runtime, { runtime, args ->
            calls += runtime.backend
            assertEquals(arguments, args)
            if (runtime.backend == WhisperBackend.VULKAN)
                WhisperProcessResult(127, "partial output must not be inserted", "libvulkan.so.1 missing")
            else WhisperProcessResult(0, "CPU result", "")
        }, { notices++ })
        repeat(3) { assertEquals("CPU result", executor.execute(arguments)) }
        assertEquals(listOf(WhisperBackend.VULKAN, WhisperBackend.CPU, WhisperBackend.CPU, WhisperBackend.CPU), calls)
        assertEquals(1, notices)
    }

    @Test
    fun `launch failure falls back but failure of both runtimes retains both errors`() {
        val executor = WhisperRuntimeExecutor(::runtime, { runtime, _ ->
            if (runtime.backend == WhisperBackend.VULKAN) throw IOException("cannot launch")
            WhisperProcessResult(1, "", "invalid model")
        })
        val error = assertThrows(IOException::class.java) { executor.execute(arguments) }
        assertTrue(error.message!!.contains("invalid model"))
        assertEquals("cannot launch", error.suppressed.single().message)
    }

    @Test
    fun `CPU only never launches Vulkan and GPU mode does not silently fall back`() {
        val calls = mutableListOf<WhisperBackend>()
        val executor = WhisperRuntimeExecutor(::runtime, { runtime, _ ->
            calls += runtime.backend
            if (runtime.backend == WhisperBackend.VULKAN) WhisperProcessResult(1, "", "GPU failed")
            else WhisperProcessResult(0, "CPU", "")
        })
        assertEquals("CPU", executor.execute(arguments, ProcessingMode.CPU_ONLY))
        assertEquals(listOf(WhisperBackend.CPU), calls)
        assertThrows(IOException::class.java) { executor.execute(arguments, ProcessingMode.GPU) }
        assertEquals(listOf(WhisperBackend.CPU, WhisperBackend.VULKAN), calls)
        executor.execute(arguments)
        executor.resetCpuFallback()
        executor.execute(arguments)
        assertEquals(3, calls.count { it == WhisperBackend.VULKAN })
    }

    @Test
    fun `cancellation never triggers a CPU retry`() {
        var calls = 0
        val executor = WhisperRuntimeExecutor(::runtime, { _, _ ->
            calls++
            throw InterruptedException("cancelled")
        })
        assertThrows(InterruptedException::class.java) { executor.execute(arguments) }
        assertEquals(1, calls)
    }
}
