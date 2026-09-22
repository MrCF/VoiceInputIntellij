package it.federico.voiceinput

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WhisperNativeProcessTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test(timeout = 15000)
    fun `large stderr cannot block stdout and CPU receives no gpu flag`() {
        assumeTrue(File("/bin/sh").canExecute())
        val directory = temporary.newFolder()
        val executable = File(directory, "whisper-cli")
        executable.writeText(
            """#!/bin/sh
            |i=0
            |while [ "${'$'}i" -lt 5000 ]; do
            |    echo 'A long diagnostic line from the native runtime to fill the stderr pipe' >&2
            |    i=${'$'}((i + 1))
            |done
            |printf '%s\n' "${'$'}@"
            |""".trimMargin()
        )
        assertTrue(executable.setExecutable(true))
        val result = WhisperNativeProcess.run(WhisperRuntime(directory, WhisperBackend.CPU), listOf("-t", "4"))
        assertEquals(0, result.exitCode)
        assertEquals("-t\n4\n--no-gpu\n", result.stdout)
        assertTrue(result.stderr.length > 300000)
    }

    @Test(timeout = 15000)
    fun `unresponsive GPU probe is terminated with an actionable timeout`() {
        assumeTrue(File("/bin/sh").canExecute())
        val directory = temporary.newFolder()
        val executable = File(directory, "gpu-probe")
        executable.writeText("#!/bin/sh\nexec sleep 60\n")
        assertTrue(executable.setExecutable(true))
        val error = assertThrows(java.io.IOException::class.java) {
            WhisperNativeProcess.probe(WhisperRuntime(directory, WhisperBackend.VULKAN), timeoutSeconds = 1)
        }
        assertTrue(error.message!!.contains("timed out"))
    }

    @Test
    fun `accelerated process preserves its exit code and does not force CPU`() {
        assumeTrue(File("/bin/sh").canExecute())
        val directory = temporary.newFolder()
        val executable = File(directory, "whisper-cli")
        executable.writeText("#!/bin/sh\nprintf '%s' \"${'$'}*\"\necho 'backend error' >&2\nexit 42\n")
        assertTrue(executable.setExecutable(true))
        val result = WhisperNativeProcess.run(WhisperRuntime(directory, WhisperBackend.VULKAN), listOf("--version"))
        assertEquals(42, result.exitCode)
        assertEquals("--version", result.stdout)
        assertEquals("backend error\n", result.stderr)
    }
}
