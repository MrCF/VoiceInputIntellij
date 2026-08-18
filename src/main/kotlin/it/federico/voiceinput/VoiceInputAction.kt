package it.federico.voiceinput

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.Messages

class VoiceInputAction : AnAction() {

    companion object {

        private val recorder =
            AudioRecorder()

        private val whisper =
            WhisperService()

        private var target: TextTarget? =
            null

        private var contextEditor: Editor? =
            null
    }

    override fun actionPerformed(e: AnActionEvent) {

        if (!recorder.isRecording) {

            target =
                TextTargetService.capture(e)

            if (target == null) {

                Messages.showWarningDialog(
                    e.project,
                    "Il campo corrente non supporta Voice Input.",
                    "Voice Input"
                )

                return
            }

            /*
             * Se la shortcut viene premuta dentro un editor di codice,
             * aggiorniamo l'ultimo editor conosciuto.
             */
            val currentEditor =
                e.getData(CommonDataKeys.EDITOR)

            if (currentEditor != null) {
                LastEditorService.update(currentEditor)
            }

            /*
             * Se siamo nell'editor usiamo quello corrente.
             * Se siamo invece nella chat AI/Codex, usiamo l'ultimo
             * editor di codice conosciuto.
             */
            contextEditor =
                currentEditor
                    ?: LastEditorService.get()

            try {

                recorder.start()

                VoiceStatusState.setRecording()

            } catch (ex: Exception) {

                target = null
                contextEditor = null

                VoiceStatusState.setReady()

                Messages.showErrorDialog(
                    e.project,
                    ex.message
                        ?: "Errore durante l'avvio della registrazione.",
                    "Voice Input"
                )
            }

            return
        }

        try {

            recorder.stop()

        } catch (ex: Exception) {

            target = null
            contextEditor = null

            VoiceStatusState.setReady()

            Messages.showErrorDialog(
                e.project,
                ex.message
                    ?: "Errore durante l'arresto della registrazione.",
                "Voice Input"
            )

            return
        }

        VoiceStatusState.setTranscribing()

        val capturedTarget =
            target

        val capturedEditor =
            contextEditor

        target = null
        contextEditor = null

        if (capturedTarget == null) {

            VoiceStatusState.setReady()

            return
        }

        val staticPrompt =
            VoiceSettings
                .getInstance()
                .state
                .prompt

        val whisperPrompt =
            WhisperPromptService.buildPrompt(
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

                                TextInsertionService.insert(
                                    capturedTarget,
                                    transcription
                                )

                            } finally {

                                VoiceStatusState.setReady()
                            }
                        }

                } catch (ex: Exception) {

                    ApplicationManager
                        .getApplication()
                        .invokeLater {

                            VoiceStatusState.setReady()

                            Messages.showErrorDialog(
                                e.project,
                                ex.message
                                    ?: "Errore durante la trascrizione.",
                                "Voice Input"
                            )
                        }
                }
            }
    }
}
