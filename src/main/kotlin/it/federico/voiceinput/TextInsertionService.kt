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
                WriteCommandAction.runWriteCommandAction(target.project) {
                    val selection = editor.selectionModel
                    val start = if (selection.hasSelection()) selection.selectionStart else editor.caretModel.offset
                    val end = if (selection.hasSelection()) selection.selectionEnd else start
                    val content = editor.document.charsSequence
                    val insertion = TranscriptionText.forInsertion(
                        text, content.getOrNull(start - 1), content.getOrNull(end)
                    )
                    editor.document.replaceString(start, end, insertion)
                    selection.removeSelection()
                    editor.caretModel.moveToOffset(start + insertion.length)
                }
            }

            is TextTarget.SwingTextComponent -> {

                val component = target.component

                val start = component.selectionStart
                val end = component.selectionEnd

                val document = component.document
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

                component.caretPosition =
                    start + insertion.length
            }
        }
    }
}
