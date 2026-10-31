package it.federico.voiceinput

import com.intellij.openapi.command.WriteCommandAction

object TextInsertionService {

    fun insert(
        target: TextTarget,
        text: String
    ) {
        if (text.isBlank()) return

        when (target) {

            is TextTarget.IntelliJEditor -> {

                val editor = target.editor
                if (editor.isDisposed || target.project.isDisposed || !target.range.isValid) return
                WriteCommandAction.runWriteCommandAction(target.project) {
                    val selection = editor.selectionModel
                    val start = if (target.replacesSelection) target.range.startOffset else target.range.endOffset
                    val end = target.range.endOffset
                    val followsTarget = selection.selectionStart == start && selection.selectionEnd == end
                    val content = editor.document.charsSequence
                    val insertion = TranscriptionText.forInsertion(
                        text, content.getOrNull(start - 1), content.getOrNull(end)
                    )
                    editor.document.replaceString(start, end, insertion)
                    if (followsTarget) {
                        selection.removeSelection()
                        editor.caretModel.moveToOffset(start + insertion.length)
                    }
                }
            }

            is TextTarget.SwingTextComponent -> {

                val component = target.component

                if (component.document !== target.document) return
                val start = target.start.offset
                val end = target.end.offset
                val followsTarget = component.selectionStart == start && component.selectionEnd == end

                val document = target.document
                val insertion = TranscriptionText.forInsertion(
                    text,
                    if (start > 0) document.getText(start - 1, 1)[0] else null,
                    if (end < document.length) document.getText(end, 1)[0] else null
                )

                component.document.remove(
                    start,
                    end - start
                )

                component.document.insertString(
                    start,
                    insertion,
                    null
                )

                if (followsTarget) {
                    component.caretPosition = start + insertion.length
                }
            }
        }
    }
}
