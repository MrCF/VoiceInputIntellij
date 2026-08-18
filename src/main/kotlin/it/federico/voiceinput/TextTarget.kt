package it.federico.voiceinput

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import javax.swing.text.JTextComponent

sealed class TextTarget {

    data class IntelliJEditor(
        val project: Project,
        val editor: Editor
    ) : TextTarget()

    data class SwingTextComponent(
        val component: JTextComponent
    ) : TextTarget()
}
