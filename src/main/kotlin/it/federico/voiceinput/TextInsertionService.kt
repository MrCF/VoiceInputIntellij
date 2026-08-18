package it.federico.voiceinput

import com.intellij.openapi.command.WriteCommandAction

object TextInsertionService {

    fun insert(
        target: TextTarget,
        text: String
    ) {
        when (target) {

            is TextTarget.IntelliJEditor -> {

                val editor = target.editor
                val offset = editor.caretModel.offset

                WriteCommandAction.runWriteCommandAction(target.project) {
                    editor.document.insertString(offset, text)

                    editor.caretModel.moveToOffset(
                        offset + text.length
                    )
                }
            }

            is TextTarget.SwingTextComponent -> {

                val component = target.component

                val start = component.selectionStart
                val end = component.selectionEnd

                component.document.remove(
                    start,
                    end - start
                )

                component.document.insertString(
                    start,
                    text,
                    null
                )

                component.caretPosition =
                    start + text.length
            }
        }
    }
}
