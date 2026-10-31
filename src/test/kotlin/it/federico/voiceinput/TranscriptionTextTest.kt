package it.federico.voiceinput

import org.junit.Assert.assertEquals
import org.junit.Test
import javax.swing.JTextArea

class TranscriptionTextTest {
    @Test
    fun `format lowercases dictation and removes final periods while preserving other punctuation`() {
        assertEquals("prima frase. poi, continuo", TranscriptionText.format(" Prima frase. Poi, continuo. \n"))
        assertEquals("davvero?", TranscriptionText.format("Davvero?"))
        assertEquals("ottimo!", TranscriptionText.format("Ottimo!"))
        assertEquals("una pausa,", TranscriptionText.format("Una pausa,"))
        assertEquals("ha detto «ciao»", TranscriptionText.format("Ha detto «CIAO.»"))
        assertEquals("continuo", TranscriptionText.format("Continuo…"))
        assertEquals("l’acqua costa 3,14 euro: è così!", TranscriptionText.format("L’acqua costa 3,14 EURO: È così!"))
        assertEquals("3.14", TranscriptionText.format("3.14"))
        assertEquals("", TranscriptionText.format("..."))
        assertEquals("", TranscriptionText.format(" \n"))
    }

    @Test
    fun `insertion respects whitespace punctuation and document boundaries`() {
        assertEquals(" ciao mondo ", TranscriptionText.forInsertion("ciao mondo", 'a', 'b'))
        assertEquals("ciao", TranscriptionText.forInsertion("ciao", '\n', ')'))
        assertEquals("ciao", TranscriptionText.forInsertion("ciao", '(', null))
        assertEquals(" ciao", TranscriptionText.forInsertion("ciao", '.', null))
        assertEquals("acqua", TranscriptionText.forInsertion("acqua", '’', null))
        assertEquals("", TranscriptionText.forInsertion("", 'a', 'b'))
    }

    @Test
    fun `successive dictations and selection replacement work in text components`() {
        val component = JTextArea()
        TextInsertionService.insert(TextTarget.SwingTextComponent(component), TranscriptionText.format("Prima parte."))
        TextInsertionService.insert(TextTarget.SwingTextComponent(component), TranscriptionText.format("Seconda parte."))
        assertEquals("prima parte seconda parte", component.text)
        component.select(6, 11)
        TextInsertionService.insert(TextTarget.SwingTextComponent(component), "frase")
        assertEquals("prima frase seconda parte", component.text)
        assertEquals(11, component.caretPosition)
        component.select(0, 5)
        TextInsertionService.insert(TextTarget.SwingTextComponent(component), "")
        assertEquals("prima frase seconda parte", component.text)
    }
}
