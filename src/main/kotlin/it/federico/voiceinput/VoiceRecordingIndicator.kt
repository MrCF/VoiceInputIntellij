package it.federico.voiceinput

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Font
import java.awt.Point
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.Timer

object VoiceRecordingIndicator :
    VoiceSessionService.Listener {

    private var editor: Editor? =
        null

    private var popup: JBPopup? =
        null

    private var label: JBLabel? =
        null

    private var timer: Timer? =
        null

    init {

        VoiceSessionService.addListener(
            this
        )
    }

    /**
     * Va chiamato prima dell'inizio
     * della registrazione.
     */
    fun setEditor(
        editor: Editor?
    ) {

        this.editor =
            editor
    }

    override fun stateChanged(
        state: VoiceSessionService.State
    ) {

        ApplicationManager
            .getApplication()
            .invokeLater {

                when (state) {

                    VoiceSessionService.State.RECORDING ->
                        showRecording()

                    VoiceSessionService.State.TRANSCRIBING ->
                        showTranscribing()

                    VoiceSessionService.State.IDLE ->
                        hide()
                }
            }
    }

    override fun transcriptionCompleted(
        text: String
    ) {
        /*
         * Per l'indicatore non serve fare nulla.
         *
         * Questo evento verrà utilizzato
         * dalla History.
         */
    }

    private fun showRecording() {

        val currentEditor =
            editor
                ?: return

        if (currentEditor.isDisposed) {
            return
        }

        hidePopupOnly()

        val indicatorLabel =
            createLabel()

        label =
            indicatorLabel

        val panel =
            createPanel(
                indicatorLabel
            )

        popup =
            JBPopupFactory
                .getInstance()
                .createComponentPopupBuilder(
                    panel,
                    null
                )
                .setRequestFocus(false)
                .setFocusable(false)
                .setMovable(false)
                .setResizable(false)
                .setCancelOnClickOutside(false)
                .setCancelOnOtherWindowOpen(false)
                .setCancelOnWindowDeactivation(false)
                .createPopup()

        updateRecordingText()

        showPopup(
            currentEditor
        )

        timer =
            Timer(
                250
            ) {

                updateRecordingText()

            }.apply {

                isRepeats =
                    true

                start()
            }
    }

    private fun showTranscribing() {

        timer
            ?.stop()

        timer =
            null

        val currentEditor =
            editor

        if (
            popup == null ||
            popup?.isDisposed == true
        ) {

            if (
                currentEditor != null &&
                !currentEditor.isDisposed
            ) {

                val indicatorLabel =
                    createLabel()

                label =
                    indicatorLabel

                val panel =
                    createPanel(
                        indicatorLabel
                    )

                popup =
                    JBPopupFactory
                        .getInstance()
                        .createComponentPopupBuilder(
                            panel,
                            null
                        )
                        .setRequestFocus(false)
                        .setFocusable(false)
                        .setMovable(false)
                        .setResizable(false)
                        .setCancelOnClickOutside(false)
                        .setCancelOnOtherWindowOpen(false)
                        .setCancelOnWindowDeactivation(false)
                        .createPopup()

                showPopup(
                    currentEditor
                )
            }
        }

        label?.text =
            "Transcribing…"
    }

    private fun updateRecordingText() {

        val duration =
            VoiceSessionService
                .recordingDurationMs

        val totalSeconds =
            duration / 1000

        val minutes =
            totalSeconds / 60

        val seconds =
            totalSeconds % 60

        label?.text =
            "● REC  %02d:%02d".format(
                minutes,
                seconds
            )
    }

    private fun createLabel():
            JBLabel {

        return JBLabel(
            "",
            SwingConstants.CENTER
        ).apply {

            font =
                font.deriveFont(
                    Font.BOLD
                )

            border =
                JBUI.Borders.empty(
                    7,
                    12
                )
        }
    }

    private fun createPanel(
        label: JBLabel
    ): JPanel {

        return JPanel(
            BorderLayout()
        ).apply {

            /*
             * Usiamo colori UI-aware:
             * funzionano sia col tema chiaro
             * sia con quello scuro.
             */
            background =
                JBColor(
                    0xF2F2F2,
                    0x3C3F41
                )

            border =
                JBUI.Borders.customLine(
                    JBColor.border(),
                    1
                )

            add(
                label,
                BorderLayout.CENTER
            )
        }
    }

    private fun showPopup(
        editor: Editor
    ) {

        val currentPopup =
            popup
                ?: return

        val component =
            editor.contentComponent

        /*
         * Posizionamento nella parte alta
         * dell'editor, leggermente spostato
         * verso destra.
         */
        val popupWidth =
            150

        val x =
            (
                    component.width -
                            popupWidth -
                            30
                    )
                .coerceAtLeast(
                    20
                )

        val y =
            20

        currentPopup.show(
            RelativePoint(
                component,
                Point(
                    x,
                    y
                )
            )
        )
    }

    private fun hide() {

        timer
            ?.stop()

        timer =
            null

        hidePopupOnly()

        editor =
            null
    }

    private fun hidePopupOnly() {

        popup
            ?.cancel()

        popup =
            null

        label =
            null
    }
}
