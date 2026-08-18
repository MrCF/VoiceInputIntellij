package it.federico.voiceinput

import com.intellij.openapi.editor.Editor

object LastEditorService {

    @Volatile
    private var lastEditor: Editor? = null

    fun update(editor: Editor?) {
        if (editor != null && !editor.isDisposed) {
            lastEditor = editor
        }
    }

    fun get(): Editor? {
        val editor = lastEditor

        return if (
            editor != null &&
            !editor.isDisposed
        ) {
            editor
        } else {
            null
        }
    }
}
