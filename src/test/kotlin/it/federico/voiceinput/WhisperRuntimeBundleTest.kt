package it.federico.voiceinput

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

class WhisperRuntimeBundleTest {
    @get:Rule
    val temporary = TemporaryFolder()
    private val manifest = "hash1  whisper-cli\nhash2  libggml-vulkan.so.0\nhash3  cpu/whisper-cli\nhash4  cpu/libggml-cpu-x64.so\n"

    @Test
    fun `runtimes are isolated cached repaired and refreshed when bundle changes`() {
        val root = temporary.newFolder()
        File(root, "whisper-cli").writeText("old runtime")
        var copies = 0
        val bundle = WhisperRuntimeBundle(root, manifest) {
            copies++
            ByteArrayInputStream(it.toByteArray())
        }
        val cpu = bundle.install(WhisperBackend.CPU)
        assertTrue(cpu.executable.canExecute())
        assertEquals("cpu/whisper-cli", cpu.executable.readText())
        assertFalse(File(cpu.directory, "libggml-vulkan.so.0").exists())
        bundle.install(WhisperBackend.CPU)
        assertEquals(2, copies)
        File(cpu.directory, "libggml-cpu-x64.so").delete()
        bundle.install(WhisperBackend.CPU)
        assertEquals(4, copies)
        val vulkan = bundle.install(WhisperBackend.VULKAN)
        assertNotEquals(cpu.directory, vulkan.directory)
        val updated = WhisperRuntimeBundle(root, manifest.replace("hash3", "newhash")) {
            ByteArrayInputStream("new runtime".toByteArray())
        }.install(WhisperBackend.CPU)
        assertNotEquals(cpu.directory, updated.directory)
        assertEquals("old runtime", File(root, "whisper-cli").readText())
    }

    @Test
    fun `interrupted extraction is retried rather than treated as installed`() {
        var fail = true
        val bundle = WhisperRuntimeBundle(temporary.newFolder(), manifest) {
            if (fail && it.endsWith(".so")) throw IOException("interrupted copy")
            ByteArrayInputStream("runtime".toByteArray())
        }
        assertThrows(IOException::class.java) { bundle.install(WhisperBackend.CPU) }
        fail = false
        assertTrue(bundle.install(WhisperBackend.CPU).executable.canExecute())
    }
}
