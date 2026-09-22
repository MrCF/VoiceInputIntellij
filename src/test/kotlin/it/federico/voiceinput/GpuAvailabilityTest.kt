package it.federico.voiceinput

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class GpuAvailabilityTest {
    @Test
    fun `availability requires successful device initialization not just a loader`() {
        assertTrue(GpuProbe.interpret(WhisperProcessResult(0, "GPU\tAMD Radeon\n", "")).available)
        assertFalse(GpuProbe.interpret(WhisperProcessResult(0, "", "")).available)
        assertFalse(GpuProbe.interpret(WhisperProcessResult(1, "GPU\tAMD Radeon\n", "initialization failed")).available)
        val missing = GpuProbe.interpret(WhisperProcessResult(127, "", "libvulkan.so.1: cannot open shared object file"))
        assertFalse(missing.available)
        assertTrue(missing.message.contains("Vulkan runtime libraries"))
        val noDevice = GpuProbe.interpret(WhisperProcessResult(3, "NO_GPU\n", ""))
        assertFalse(noDevice.available)
        assertTrue(noDevice.message.contains("No compatible Vulkan GPU"))
    }

    @Test
    fun `requests run off caller thread coalesce cache and recheck explicitly`() {
        val pool = Executors.newSingleThreadExecutor()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val enabled = AtomicInteger()
        val caller = Thread.currentThread()
        val monitor = GpuAvailabilityMonitor(pool, {
            assertNotSame(caller, Thread.currentThread())
            calls.incrementAndGet()
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            GpuAvailability(true, "GPU available")
        }, { enabled.incrementAndGet() })
        try {
            val first = monitor.check()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertSame(first, monitor.check())
            assertSame(first, monitor.check(force = true))
            release.countDown()
            assertTrue(first.get(5, TimeUnit.SECONDS).available)
            repeat(1000) { assertTrue(monitor.check().get().available) }
            assertEquals(1, calls.get())
            monitor.transcriptionFailed()
            assertFalse(monitor.check().get().available)
            assertEquals(1, calls.get())
            assertTrue(monitor.check(force = true).get(5, TimeUnit.SECONDS).available)
            assertEquals(2, calls.get())
            assertEquals(2, enabled.get())
        } finally {
            release.countDown()
            monitor.close()
            pool.shutdownNow()
        }
    }

    @Test
    fun `failed probe produces cached usable CPU status`() {
        val pool = Executors.newSingleThreadExecutor()
        val monitor = GpuAvailabilityMonitor(pool, { throw IllegalStateException("bad runtime") })
        try {
            val result = monitor.check().get(5, TimeUnit.SECONDS)
            assertFalse(result.available)
            assertTrue(result.checked)
            assertTrue(result.message.contains("CPU processing is available"))
        } finally {
            monitor.close()
            pool.shutdownNow()
        }
    }

    @Test
    fun `closing cancels a running check and stops its background task`() {
        val pool = Executors.newSingleThreadExecutor()
        val entered = CountDownLatch(1)
        val exited = CountDownLatch(1)
        val monitor = GpuAvailabilityMonitor(pool, {
            entered.countDown()
            try {
                CountDownLatch(1).await()
                GpuAvailability(true, "unexpected")
            } finally {
                exited.countDown()
            }
        })
        try {
            val result = monitor.check()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            monitor.close()
            assertTrue(result.isCancelled)
            assertTrue(exited.await(5, TimeUnit.SECONDS))
        } finally {
            monitor.close()
            pool.shutdownNow()
        }
    }

    @Test
    fun `GPU row cannot be selected while unavailable including keyboard selection`() {
        javax.swing.SwingUtilities.invokeAndWait {
            val model = ProcessingModeModel()
            model.selectedItem = ProcessingMode.GPU
            assertEquals(ProcessingMode.AUTOMATIC, model.selectedItem)
            model.selectedItem = ProcessingMode.CPU_ONLY
            assertEquals(ProcessingMode.CPU_ONLY, model.selectedItem)
            model.gpuAvailable = true
            model.selectedItem = ProcessingMode.GPU
            assertEquals(ProcessingMode.GPU, model.selectedItem)
            model.gpuAvailable = false
            model.selectedItem = ProcessingMode.AUTOMATIC
            model.selectedItem = ProcessingMode.GPU
            assertEquals(ProcessingMode.AUTOMATIC, model.selectedItem)
            model.restore(ProcessingMode.GPU)
            assertEquals(ProcessingMode.GPU, model.selectedItem)
        }
    }
}
