package it.federico.voiceinput

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer

class VoiceStatusWidgetFactory :
    StatusBarWidgetFactory {

    companion object {

        const val WIDGET_ID =
            "VoiceInputStatus"
    }

    override fun getId(): String =
        WIDGET_ID

    override fun getDisplayName(): String =
        "Voice Input"

    override fun isAvailable(
        project: Project
    ): Boolean =
        true

    override fun createWidget(
        project: Project
    ): StatusBarWidget {

        return VoiceStatusWidget(
            project
        )
    }

    override fun disposeWidget(
        widget: StatusBarWidget
    ) {

        Disposer.dispose(
            widget
        )
    }

    override fun canBeEnabledOn(
        statusBar: StatusBar
    ): Boolean =
        true
}

class VoiceStatusWidget(
    private val project: Project
) :
    CustomStatusBarWidget,
    VoiceSessionService.Listener {

    companion object {

        /*
         * Action specifica PTT.
         */
        private const val PTT_ACTION_ID =
            "VoiceInputPushToTalkAction"

        private const val ACTION_PLACE =
            "VoiceInputStatusPushToTalk"
    }

    private val label =
        JBLabel(
            "🎙 Hold to talk"
        ).apply {

            border =
                JBUI.Borders.empty(
                    0,
                    6
                )

            cursor =
                Cursor.getPredefinedCursor(
                    Cursor.HAND_CURSOR
                )

            toolTipText =
                "Hold the mouse button to dictate"
        }

    private val component =
        JPanel(
            BorderLayout()
        ).apply {

            isOpaque =
                false

            cursor =
                Cursor.getPredefinedCursor(
                    Cursor.HAND_CURSOR
                )

            add(
                label,
                BorderLayout.CENTER
            )
        }

    private var statusBar:
            StatusBar? =
        null

    private var pttPressed =
        false

    private var timer:
            Timer? =
        null

    init {

        installMouseHandler()

        VoiceSessionService
            .addListener(
                this
            )

        updateIdleState()
    }

    override fun ID(): String =
        VoiceStatusWidgetFactory
            .WIDGET_ID

    override fun getComponent():
            JComponent {

        return component
    }

    override fun install(
        statusBar: StatusBar
    ) {

        this.statusBar =
            statusBar

        VoiceStatusState.statusBar =
            statusBar
    }

    override fun dispose() {

        stopTimer()

        VoiceSessionService
            .removeListener(
                this
            )

        if (
            VoiceStatusState.statusBar ===
            statusBar
        ) {

            VoiceStatusState.statusBar =
                null
        }

        statusBar =
            null
    }

    override fun stateChanged(
        state: VoiceSessionService.State
    ) {

        ApplicationManager
            .getApplication()
            .invokeLater {

                when (state) {

                    VoiceSessionService.State.IDLE -> {

                        pttPressed =
                            false

                        updateIdleState()
                    }

                    VoiceSessionService.State.RECORDING -> {

                        updateRecordingState()
                    }

                    VoiceSessionService.State.TRANSCRIBING -> {

                        updateTranscribingState()
                    }
                }
            }
    }

    override fun transcriptionCompleted(
        text: String
    ) {
        // Nulla da fare.
    }

    private fun installMouseHandler() {

        val mouseHandler =
            object :
                MouseAdapter() {

                override fun mousePressed(
                    event: MouseEvent
                ) {

                    if (
                        event.button !=
                        MouseEvent.BUTTON1
                    ) {
                        return
                    }

                    if (
                        VoiceSessionService
                            .isRecording ||
                        VoiceSessionService
                            .isTranscribing
                    ) {
                        return
                    }

                    /*
                     * Impostiamo true solamente
                     * quando stiamo realmente
                     * tentando un PTT.
                     */
                    pttPressed =
                        true

                    invokePushToTalkAction(
                        event
                    )
                }

                override fun mouseReleased(
                    event: MouseEvent
                ) {

                    if (
                        event.button !=
                        MouseEvent.BUTTON1
                    ) {
                        return
                    }

                    if (!pttPressed) {
                        return
                    }

                    pttPressed =
                        false

                    /*
                     * In PTT l'Automatic Stop
                     * non può essere intervenuto.
                     *
                     * Finché il mouse è premuto
                     * dobbiamo quindi essere ancora
                     * in RECORDING.
                     */
                    if (
                        VoiceSessionService
                            .isRecording
                    ) {

                        VoiceInputController
                            .stopAndTranscribe(
                                project
                            )
                    }
                }
            }

        component.addMouseListener(
            mouseHandler
        )

        label.addMouseListener(
            mouseHandler
        )
    }

    private fun invokePushToTalkAction(
        event: MouseEvent
    ) {

        val action =
            ActionManager
                .getInstance()
                .getAction(
                    PTT_ACTION_ID
                )

        if (action == null) {

            pttPressed =
                false

            return
        }

        ActionManager
            .getInstance()
            .tryToExecute(
                action,
                event,
                component,
                ACTION_PLACE,
                true
            )
    }

    private fun updateIdleState() {

        stopTimer()

        label.text =
            "🎙 Hold to talk"

        label.toolTipText =
            "Hold the mouse button to dictate"
    }

    private fun updateRecordingState() {

        updateRecordingText()

        label.toolTipText =
            "Release the mouse button to transcribe"

        stopTimer()

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

    private fun updateRecordingText() {

        val durationMs =
            VoiceSessionService
                .recordingDurationMs

        val totalSeconds =
            durationMs /
                    1000

        val minutes =
            totalSeconds /
                    60

        val seconds =
            totalSeconds %
                    60

        label.text =
            "● REC  %02d:%02d".format(
                minutes,
                seconds
            )
    }

    private fun updateTranscribingState() {

        stopTimer()

        label.text =
            "Transcribing…"

        label.toolTipText =
            "Voice Input is transcribing"
    }

    private fun stopTimer() {

        timer
            ?.stop()

        timer =
            null
    }
}
