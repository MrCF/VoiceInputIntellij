package it.federico.voiceinput

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VoiceHistoryServiceTest {

    private lateinit var service: VoiceHistoryService

    @Before
    fun setUp() {
        service = VoiceHistoryService()
    }

    @After
    fun tearDown() {
        service.dispose()
    }

    @Test
    fun `new entries are trimmed and stored newest first`() {
        service.transcriptionCompleted("  first  ")
        service.transcriptionCompleted("second")

        assertEquals(listOf("second", "first"), service.getEntries().map { it.text })
    }

    @Test
    fun `blank entries are ignored and history is limited to fifty items`() {
        service.transcriptionCompleted("   ")
        repeat(60) { service.transcriptionCompleted("entry-$it") }

        val entries = service.getEntries()
        assertEquals(50, entries.size)
        assertEquals("entry-59", entries.first().text)
        assertEquals("entry-10", entries.last().text)
    }

    @Test
    fun `remove and clear notify listeners only when history changes`() {
        var notifications = 0
        service.addListener(object : VoiceHistoryService.Listener {
            override fun historyChanged() {
                notifications++
            }
        })

        service.transcriptionCompleted("one")
        val entry = service.getEntries().single()
        service.remove(entry)
        service.remove(entry)
        service.clear()

        assertTrue(service.getEntries().isEmpty())
        assertEquals(2, notifications)
    }
}
