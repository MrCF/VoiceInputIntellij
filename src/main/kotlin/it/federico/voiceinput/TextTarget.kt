package it.federico.voiceinput

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import javax.swing.text.Document
import javax.swing.text.JTextComponent
import javax.swing.text.Position

sealed class TextTarget : AutoCloseable {

    override fun close() {}

    data class IntelliJEditor(
        val project: Project,
        val editor: Editor,
        val range: RangeMarker = editor.document.createRangeMarker(
            editor.selectionModel.selectionStart, editor.selectionModel.selectionEnd
        ).apply { isGreedyToRight = !editor.selectionModel.hasSelection() }
    ) : TextTarget() {
        val replacesSelection = editor.selectionModel.hasSelection()

        override fun close() = range.dispose()
    }

    data class SwingTextComponent(
        val component: JTextComponent,
        val document: Document = component.document,
        val start: Position = document.createPosition(component.selectionStart),
        val end: Position = document.createPosition(component.selectionEnd)
    ) : TextTarget()
}
