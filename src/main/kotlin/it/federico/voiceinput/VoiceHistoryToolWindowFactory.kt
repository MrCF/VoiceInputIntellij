package it.federico.voiceinput

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.Component
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.swing.*

class VoiceHistoryToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = VoiceHistoryPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }
}

private class VoiceHistoryPanel(
    private val project: Project
) : JPanel(BorderLayout()), VoiceHistoryService.Listener, Disposable {

    companion object {
        private const val ACTION_PLACE = "VoiceInputHistory"
        private const val PREVIEW_LIMIT = 100
    }

    private val historyService = VoiceHistoryService.getInstance()
    private val model = DefaultListModel<VoiceHistoryService.Entry>()
    private val list = JBList(model)
    private var disposed = false

    private val insertAction = object : DumbAwareAction(
        VoiceInputBundle.message("history.action.insert"),
        VoiceInputBundle.message("history.action.insert.description"),
        AllIcons.Actions.MenuPaste
    ) {
        override fun actionPerformed(e: AnActionEvent) = insertSelected()
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = list.selectedValue != null && currentEditorIsWritable()
        }
    }

    private val copyAction = object : DumbAwareAction(
        VoiceInputBundle.message("history.action.copy"),
        VoiceInputBundle.message("history.action.copy.description"),
        AllIcons.Actions.Copy
    ) {
        override fun actionPerformed(e: AnActionEvent) = copySelected()
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = list.selectedValue != null
        }
    }

    private val removeAction = object : DumbAwareAction(
        VoiceInputBundle.message("history.action.remove"),
        VoiceInputBundle.message("history.action.remove.description"),
        AllIcons.Actions.GC
    ) {
        override fun actionPerformed(e: AnActionEvent) = removeSelected()
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = list.selectedValue != null
        }
    }

    private val clearAction = object : DumbAwareAction(
        VoiceInputBundle.message("history.action.clear"),
        VoiceInputBundle.message("history.action.clear.description"),
        AllIcons.Actions.DeleteTag
    ) {
        override fun actionPerformed(e: AnActionEvent) = clearHistory()
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = !model.isEmpty
        }
    }

    init {
        configureList()
        val actions = DefaultActionGroup(insertAction, copyAction, removeAction, clearAction)
        val toolbar = ActionManager.getInstance().createActionToolbar(ACTION_PLACE, actions, true)
        toolbar.targetComponent = list

        add(toolbar.component, BorderLayout.NORTH)
        add(JBScrollPane(list), BorderLayout.CENTER)

        registerShortcuts()
        historyService.addListener(this)
        reload()
    }

    private fun configureList() {
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.cellRenderer = HistoryCellRenderer(
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("dd MMM, HH:mm"),
            PREVIEW_LIMIT
        )
        list.emptyText.text = VoiceInputBundle.message("history.empty")
        list.emptyText.appendSecondaryText(
            VoiceInputBundle.message("history.empty.description"),
            com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES,
            null
        )
        list.addListSelectionListener { updateActions() }
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2 && SwingUtilities.isLeftMouseButton(event)) insertSelected()
            }
        })
    }

    private fun registerShortcuts() {
        insertAction.registerCustomShortcutSet(CommonShortcuts.ENTER, list, this)
        copyAction.registerCustomShortcutSet(CommonShortcuts.getCopy(), list, this)
        removeAction.registerCustomShortcutSet(
            ShortcutSet { arrayOf(KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), null)) },
            list,
            this
        )
    }

    override fun historyChanged() {
        ApplicationManager.getApplication().invokeLater {
            if (!disposed && !project.isDisposed) reload()
        }
    }

    private fun reload() {
        val selected = list.selectedValue
        val entries = historyService.getEntries()
        model.clear()
        model.addAll(entries)
        selected?.let { previous ->
            entries.indexOf(previous).takeIf { it >= 0 }?.let { list.selectedIndex = it }
        }
        updateActions()
    }

    private fun updateActions() {
        insertAction.templatePresentation.isEnabled = list.selectedValue != null && currentEditorIsWritable()
        copyAction.templatePresentation.isEnabled = list.selectedValue != null
        removeAction.templatePresentation.isEnabled = list.selectedValue != null
        clearAction.templatePresentation.isEnabled = !model.isEmpty
    }

    private fun copySelected() {
        val entry = list.selectedValue ?: return
        CopyPasteManager.getInstance().setContents(StringSelection(entry.text))
    }

    private fun insertSelected() {
        val entry = list.selectedValue ?: return
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        if (editor.isDisposed || !FileDocumentManager.getInstance().requestWriting(editor.document, project)) return

        WriteCommandAction.runWriteCommandAction(project) {
            editor.caretModel.allCarets
                .sortedByDescending { it.selectionStart }
                .forEach { caret ->
                    val start = caret.selectionStart
                    val end = caret.selectionEnd
                    editor.document.replaceString(start, end, entry.text)
                    caret.moveToOffset(start + entry.text.length)
                    caret.removeSelection()
                }
        }
        editor.contentComponent.requestFocusInWindow()
        updateActions()
    }

    private fun currentEditorIsWritable(): Boolean {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return false
        return !editor.isDisposed && editor.document.isWritable
    }

    private fun removeSelected() {
        list.selectedValue?.let(historyService::remove)
    }

    private fun clearHistory() {
        if (model.isEmpty) return
        val answer = Messages.showYesNoDialog(
            project,
            VoiceInputBundle.message("history.clear.confirmation"),
            VoiceInputBundle.message("history.clear.title"),
            Messages.getQuestionIcon()
        )
        if (answer == Messages.YES) historyService.clear()
    }

    override fun dispose() {
        disposed = true
        historyService.removeListener(this)
    }
}

private class HistoryCellRenderer(
    private val todayFormatter: DateTimeFormatter,
    private val olderFormatter: DateTimeFormatter,
    private val previewLimit: Int
) : DefaultListCellRenderer() {

    companion object {
        private val LINE_BREAKS = Regex("[\\r\\n]+")
    }

    override fun getListCellRendererComponent(
        list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
    ): Component {
        val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        val entry = value as? VoiceHistoryService.Entry ?: return component
        val formatter = if (entry.timestamp.toLocalDate() == LocalDate.now()) todayFormatter else olderFormatter
        val preview = entry.text.replace(LINE_BREAKS, " ").let {
            if (it.length > previewLimit) it.take(previewLimit) + "…" else it
        }

        text = "${entry.timestamp.format(formatter)}   $preview"
        toolTipText = entry.text.takeIf { it.length <= previewLimit * 5 }
        return component
    }
}
