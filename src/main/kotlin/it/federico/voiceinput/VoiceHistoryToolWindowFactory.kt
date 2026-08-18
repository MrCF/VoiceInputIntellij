package it.federico.voiceinput

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.datatransfer.StringSelection
import java.time.format.DateTimeFormatter
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities

class VoiceHistoryToolWindowFactory :
    ToolWindowFactory {

    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow
    ) {

        val panel =
            VoiceHistoryPanel(
                project
            )

        val content =
            ContentFactory
                .getInstance()
                .createContent(
                    panel,
                    "",
                    false
                )

        toolWindow
            .contentManager
            .addContent(
                content
            )
    }
}

private class VoiceHistoryPanel(
    private val project: Project
) :
    JPanel(
        BorderLayout()
    ),
    VoiceHistoryService.Listener {

    private val historyService =
        VoiceHistoryService
            .getInstance()

    private val model =
        DefaultListModel<
                VoiceHistoryService.Entry
                >()

    private val list =
        JBList(model)

    private val insertButton =
        JButton("Insert")

    private val copyButton =
        JButton("Copy")

    private val clearButton =
        JButton("Clear")

    private val timeFormatter =
        DateTimeFormatter.ofPattern(
            "HH:mm"
        )

    init {

        list.selectionMode =
            ListSelectionModel
                .SINGLE_SELECTION

        list.cellRenderer =
            HistoryCellRenderer(
                timeFormatter
            )

        list.addListSelectionListener {

            updateButtons()
        }

        /*
         * Doppio click = Insert
         */
        list.addMouseListener(
            object :
                java.awt.event.MouseAdapter() {

                override fun mouseClicked(
                    event:
                    java.awt.event.MouseEvent
                ) {

                    if (
                        event.clickCount == 2 &&
                        SwingUtilities
                            .isLeftMouseButton(
                                event
                            )
                    ) {

                        insertSelected()
                    }
                }
            }
        )

        insertButton.addActionListener {

            insertSelected()
        }

        copyButton.addActionListener {

            copySelected()
        }

        clearButton.addActionListener {

            clearHistory()
        }

        val buttons =
            JPanel(
                FlowLayout(
                    FlowLayout.LEFT
                )
            )

        buttons.add(
            insertButton
        )

        buttons.add(
            copyButton
        )

        buttons.add(
            clearButton
        )

        add(
            JBScrollPane(
                list
            ),
            BorderLayout.CENTER
        )

        add(
            buttons,
            BorderLayout.SOUTH
        )

        /*
         * Da questo momento la Tool Window
         * viene aggiornata quando cambia
         * la History.
         */
        historyService.addListener(
            this
        )

        reload()
    }

    override fun historyChanged() {

        ApplicationManager
            .getApplication()
            .invokeLater {

                reload()
            }
    }

    private fun reload() {

        val selected =
            list.selectedValue

        val entries =
            historyService
                .getEntries()

        model.clear()

        entries.forEach {

            model.addElement(
                it
            )
        }

        /*
         * Se possibile manteniamo
         * la selezione precedente.
         */
        if (selected != null) {

            val index =
                entries.indexOf(
                    selected
                )

            if (index >= 0) {

                list.selectedIndex =
                    index
            }
        }

        updateButtons()
    }

    private fun updateButtons() {

        val selected =
            list.selectedValue !=
                    null

        insertButton.isEnabled =
            selected

        copyButton.isEnabled =
            selected

        clearButton.isEnabled =
            model.size > 0
    }

    private fun copySelected() {

        val entry =
            list.selectedValue
                ?: return

        CopyPasteManager
            .getInstance()
            .setContents(
                StringSelection(
                    entry.text
                )
            )
    }

    private fun insertSelected() {

        val entry =
            list.selectedValue
                ?: return

        /*
         * Per ora Insert opera
         * sull'ultimo editor IntelliJ.
         */
        val editor =
            LastEditorService.get()
                ?: return

        if (editor.isDisposed) {
            return
        }

        ApplicationManager
            .getApplication()
            .runWriteAction {

                val document =
                    editor.document

                val caret =
                    editor.caretModel

                val offset =
                    caret.offset

                document.insertString(
                    offset,
                    entry.text
                )

                caret.moveToOffset(
                    offset +
                            entry.text.length
                )
            }

        editor
            .contentComponent
            .requestFocusInWindow()
    }

    private fun clearHistory() {

        historyService.clear()
    }
}

private class HistoryCellRenderer(
    private val formatter:
    DateTimeFormatter
) :
    DefaultListCellRenderer() {

    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {

        val component =
            super
                .getListCellRendererComponent(
                    list,
                    value,
                    index,
                    isSelected,
                    cellHasFocus
                )

        val entry =
            value as?
                    VoiceHistoryService.Entry

        if (entry != null) {

            val time =
                entry.timestamp
                    .format(
                        formatter
                    )

            val preview =
                entry.text
                    .replace(
                        '\n',
                        ' '
                    )
                    .replace(
                        '\r',
                        ' '
                    )
                    .let {

                        if (
                            it.length >
                            100
                        ) {

                            it.take(
                                100
                            ) + "…"

                        } else {

                            it
                        }
                    }

            text =
                "$time   $preview"

            toolTipText =
                entry.text
        }

        return component
    }
}
