package it.federico.voiceinput

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import java.awt.KeyboardFocusManager
import javax.swing.text.JTextComponent

object TextTargetService {

    fun capture(e: AnActionEvent): TextTarget? {

        // Prima proviamo un normale editor IntelliJ
        val editor = e.getData(CommonDataKeys.EDITOR)
        val project = e.project

        if (editor != null && project != null) {
            return TextTarget.IntelliJEditor(
                project,
                editor
            )
        }

        // Altrimenti vediamo quale componente Swing possiede il focus
        val component =
            KeyboardFocusManager
                .getCurrentKeyboardFocusManager()
                .focusOwner

        if (component is JTextComponent && component.isEditable) {
            return TextTarget.SwingTextComponent(component)
        }

        return null
    }
}
