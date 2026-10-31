package it.federico.voiceinput

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.swing.JTextArea

class TextInsertionServiceTest : BasePlatformTestCase() {
    fun testEditorKeepsCapturedPositionAndNewCaret() {
        myFixture.configureByText("test.txt", "prima dopo")
        val editor = myFixture.editor
        editor.caretModel.moveToOffset(6)
        val target = TextTarget.IntelliJEditor(project, editor)
        try {
            editor.caretModel.moveToOffset(0)
            TextInsertionService.insert(target, "dettato")
            assertEquals("prima dettato dopo", editor.document.text)
            assertEquals(0, editor.caretModel.offset)
        } finally {
            target.close()
        }
    }

    fun testEditorPositionTracksEditsBeforeIt() {
        myFixture.configureByText("test.txt", "prima dopo")
        val editor = myFixture.editor
        editor.caretModel.moveToOffset(6)
        val target = TextTarget.IntelliJEditor(project, editor)
        try {
            WriteCommandAction.runWriteCommandAction(project) { editor.document.insertString(0, "inizio ") }
            editor.caretModel.moveToOffset(0)
            TextInsertionService.insert(target, "dettato")
            assertEquals("inizio prima dettato dopo", editor.document.text)
        } finally {
            target.close()
        }
    }

    fun testCapturedEditorSelectionIsReplaced() {
        myFixture.configureByText("test.txt", "prima vecchio dopo")
        val editor = myFixture.editor
        editor.selectionModel.setSelection(6, 13)
        val target = TextTarget.IntelliJEditor(project, editor)
        try {
            editor.selectionModel.removeSelection()
            editor.caretModel.moveToOffset(0)
            TextInsertionService.insert(target, "nuovo")
            assertEquals("prima nuovo dopo", editor.document.text)
            assertEquals(0, editor.caretModel.offset)
        } finally {
            target.close()
        }
    }

    fun testQueuedDictationsAtSamePositionKeepTheirOrder() {
        myFixture.configureByText("test.txt", "prima ")
        val editor = myFixture.editor
        editor.caretModel.moveToOffset(6)
        val first = TextTarget.IntelliJEditor(project, editor)
        val second = TextTarget.IntelliJEditor(project, editor)
        try {
            editor.caretModel.moveToOffset(0)
            TextInsertionService.insert(first, "uno")
            TextInsertionService.insert(second, "due")
            assertEquals("prima uno due", editor.document.text)
            assertEquals(0, editor.caretModel.offset)
        } finally {
            first.close()
            second.close()
        }
    }

    fun testRepeatedCaptureOnLargeDocument() {
        myFixture.configureByText("test.txt", "a".repeat(1_000_000))
        val editor = myFixture.editor
        editor.caretModel.moveToOffset(500_000)
        repeat(1_000) {
            val target = TextTarget.IntelliJEditor(project, editor)
            try {
                assertEquals(500_000, target.range.startOffset)
                assertEquals(500_000, target.range.endOffset)
            } finally {
                target.close()
            }
        }
        assertEquals(1_000_000, editor.document.textLength)
    }

    fun testSwingKeepsCapturedPositionAndTracksEdits() {
        val component = JTextArea("prima dopo")
        component.caretPosition = 6
        val target = TextTarget.SwingTextComponent(component)
        component.document.insertString(0, "inizio ", null)
        component.caretPosition = 0
        TextInsertionService.insert(target, "dettato")
        assertEquals("inizio prima dettato dopo", component.text)
        assertEquals(0, component.caretPosition)
    }

    fun testSwingReplacesOriginalSelection() {
        val component = JTextArea("prima vecchio dopo")
        component.select(6, 13)
        val target = TextTarget.SwingTextComponent(component)
        component.caretPosition = 0
        TextInsertionService.insert(target, "nuovo")
        assertEquals("prima nuovo dopo", component.text)
        assertEquals(0, component.caretPosition)
    }
}
