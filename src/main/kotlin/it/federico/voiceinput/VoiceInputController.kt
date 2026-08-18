package it.federico.voiceinput

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

object VoiceInputController {

    private val recorder =
        AudioRecorder()

    private val whisper =
        WhisperService()

    private var target:
            TextTarget? =
        null

    private var contextEditor:
            Editor? =
        null

    @Volatile
    private var transcriptionStarting =
        false

    val isRecording: Boolean
        get() =
            VoiceSessionService
                .isRecording

    val isTranscribing: Boolean
        get() =
            VoiceSessionService
                .isTranscribing

    /**
     * Modalità normale Meta+V.
     */
    fun toggle(
        e: AnActionEvent
    ) {

        if (isTranscribing) {
            return
        }

        if (isRecording) {

            stopAndTranscribe(
                e.project
            )

        } else {

            startToggle(
                e
            )
        }
    }

    /**
     * Registrazione normale.
     *
     * Automatic Stop consentito.
     */
    fun startToggle(
        e: AnActionEvent
    ): Boolean {

        return startRecording(
            e =
                e,

            autoStopAllowed =
                true
        )
    }

    /**
     * Push-To-Talk.
     *
     * Automatic Stop ESPRESSAMENTE
     * disabilitato.
     */
    fun startPushToTalk(
        e: AnActionEvent
    ): Boolean {

        return startRecording(
            e =
                e,

            autoStopAllowed =
                false
        )
    }

    private fun startRecording(
        e: AnActionEvent,
        autoStopAllowed: Boolean
    ): Boolean {

        if (
            isRecording ||
            isTranscribing
        ) {
            return false
        }

        /*
         * History inizializzata prima
         * della prima trascrizione.
         */
        VoiceHistoryService
            .getInstance()

        target =
            TextTargetService.capture(
                e
            )

        if (target == null) {

            Messages.showWarningDialog(
                e.project,
                "Il campo corrente non supporta Voice Input.",
                "Voice Input"
            )

            return false
        }

        val currentEditor =
            e.getData(
                CommonDataKeys.EDITOR
            )

        if (currentEditor != null) {

            LastEditorService.update(
                currentEditor
            )
        }

        contextEditor =
            currentEditor
                ?: LastEditorService.get()

        VoiceRecordingIndicator
            .setEditor(
                contextEditor
            )

        return try {

            val project =
                e.project

            recorder.start(

                autoStopAllowed =
                    autoStopAllowed,

                onAutoStop = {

                    /*
                     * Questo callback potrà arrivare
                     * solo nella modalità normale,
                     * perché PTT passa
                     * autoStopAllowed = false.
                     */
                    ApplicationManager
                        .getApplication()
                        .invokeLater {

                            startTranscription(
                                project
                            )
                        }
                }
            )

            VoiceSessionService
                .setRecording()

            true

        } catch (ex: Exception) {

            clearCapturedContext()

            VoiceSessionService
                .setIdle()

            Messages.showErrorDialog(
                e.project,
                ex.message
                    ?: "Errore durante l'avvio della registrazione.",
                "Voice Input"
            )

            false
        }
    }

    /**
     * Usato sia da Meta+V
     * sia dal rilascio PTT.
     */
    fun stopAndTranscribe(
        project: Project?
    ) {

        if (!isRecording) {
            return
        }

        try {

            recorder.stop()

        } catch (ex: Exception) {

            clearCapturedContext()

            VoiceSessionService
                .setIdle()

            Messages.showErrorDialog(
                project,
                ex.message
                    ?: "Errore durante l'arresto della registrazione.",
                "Voice Input"
            )

            return
        }

        startTranscription(
            project
        )
    }

    private fun startTranscription(
        project: Project?
    ) {

        synchronized(this) {

            if (
                transcriptionStarting ||
                VoiceSessionService
                    .isTranscribing
            ) {
                return
            }

            transcriptionStarting =
                true
        }

        val capturedTarget =
            target

        val capturedEditor =
            contextEditor

        clearCapturedContext()

        if (capturedTarget == null) {

            transcriptionStarting =
                false

            VoiceSessionService
                .setIdle()

            return
        }

        VoiceSessionService
            .setTranscribing()

        val staticPrompt =
            VoiceSettings
                .getInstance()
                .state
                .prompt

        val whisperPrompt =
            WhisperPromptService
                .buildPrompt(
                    capturedEditor,
                    staticPrompt
                )

        ApplicationManager
            .getApplication()
            .executeOnPooledThread {

                try {

                    val transcription =
                        whisper.transcribe(
                            recorder.outputFile,
                            whisperPrompt
                        )

                    ApplicationManager
                        .getApplication()
                        .invokeLater {

                            try {

                                VoiceSessionService
                                    .transcriptionCompleted(
                                        transcription
                                    )

                                TextInsertionService
                                    .insert(
                                        capturedTarget,
                                        transcription
                                    )

                            } finally {

                                transcriptionStarting =
                                    false

                                VoiceSessionService
                                    .setIdle()
                            }
                        }

                } catch (ex: Exception) {

                    ApplicationManager
                        .getApplication()
                        .invokeLater {

                            transcriptionStarting =
                                false

                            VoiceSessionService
                                .setIdle()

                            Messages.showErrorDialog(
                                project,
                                ex.message
                                    ?: "Errore durante la trascrizione.",
                                "Voice Input"
                            )
                        }
                }
            }
    }

    private fun clearCapturedContext() {

        target =
            null

        contextEditor =
            null
    }
}
