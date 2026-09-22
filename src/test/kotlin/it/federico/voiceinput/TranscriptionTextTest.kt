package it.federico.voiceinput

import org.junit.Assert.assertEquals
import org.junit.Test
import javax.swing.JTextArea

class TranscriptionTextTest {
    @Test
    fun `default removes final periods but keeps internal punctuation and intent`() {
        val mode = PunctuationMode.WITHOUT_FINAL_PERIOD
        assertEquals("Prima frase. Poi, continuo", TranscriptionText.format(" Prima frase. Poi, continuo. \n", mode))
        for (text in listOf("Davvero?", "Ottimo!", "Una pausa,", "3.14")) {
            assertEquals(text, TranscriptionText.format(text, mode))
        }
        assertEquals("Ha detto «ciao»", TranscriptionText.format("Ha detto «ciao.»", mode))
        assertEquals("Continuo", TranscriptionText.format("Continuo…", mode))
        assertEquals("", TranscriptionText.format("...", mode))
    }

    @Test
    fun `none removes sentence punctuation without merging words or changing apostrophes and decimals`() {
        assertEquals(
            "L’acqua costa 3,14 euro davvero sì", TranscriptionText.format(
                "L’acqua costa 3,14 euro: davvero? (sì!)", PunctuationMode.NONE
            )
        )
        assertEquals("uno due tre", TranscriptionText.format("uno,due—tre.", PunctuationMode.NONE))
        assertEquals("", TranscriptionText.format("?! …", PunctuationMode.NONE))
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
        val target = TextTarget.SwingTextComponent(component)
        TextInsertionService.insert(target, "Prima parte")
        TextInsertionService.insert(target, "seconda parte")
        assertEquals("Prima parte seconda parte", component.text)
        component.select(6, 11)
        TextInsertionService.insert(target, "frase")
        assertEquals("Prima frase seconda parte", component.text)
        assertEquals(11, component.caretPosition)
        component.select(0, 5)
        TextInsertionService.insert(target, "")
        assertEquals("Prima frase seconda parte", component.text)
    }
}
