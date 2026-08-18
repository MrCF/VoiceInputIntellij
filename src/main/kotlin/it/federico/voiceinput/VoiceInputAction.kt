package it.federico.voiceinput

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
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

        /*
         * Evita che stop manuale e auto-stop
         * facciano partire due trascrizioni insieme.
         */
        @Volatile
        private var transcribing =
            false
    }

    override fun actionPerformed(
        e: AnActionEvent
    ) {

        /*
         * Se Whisper sta già lavorando,
         * ignoriamo ulteriori pressioni.
         */
        if (transcribing) {
            return
        }

        /*
         * PRIMA PRESSIONE:
         * cattura target/editor e avvia registrazione.
         */
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

            try {

                val project =
                    e.project

                recorder.start(

                    onAutoStop = {

                        /*
                         * Il callback arriva dal thread
                         * del recorder.
                         *
                         * Torniamo sul thread UI prima
                         * di modificare stato/UI IntelliJ.
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

                VoiceStatusState
                    .setRecording()

            } catch (ex: Exception) {

                target = null
                contextEditor = null

                VoiceStatusState
                    .setReady()

                Messages.showErrorDialog(
                    e.project,
                    ex.message
                        ?: "Errore durante l'avvio della registrazione.",
                    "Voice Input"
                )
            }

            return
        }

        /*
         * SECONDA PRESSIONE:
         * stop manuale.
         */
        try {

            recorder.stop()

        } catch (ex: Exception) {

            target = null
            contextEditor = null

            VoiceStatusState
                .setReady()

            Messages.showErrorDialog(
                e.project,
                ex.message
                    ?: "Errore durante l'arresto della registrazione.",
                "Voice Input"
            )

            return
        }

        /*
         * Dopo lo stop manuale avviamo
         * lo stesso identico flusso usato
         * dall'auto-stop.
         */
        startTranscription(
            e.project
        )
    }

    private fun startTranscription(
        project: Project?
    ) {

        /*
         * Protezione contro doppio avvio.
         */
        if (transcribing) {
            return
        }

        val capturedTarget =
            target

        val capturedEditor =
            contextEditor

        target = null
        contextEditor = null

        if (capturedTarget == null) {

            VoiceStatusState
                .setReady()

            return
        }

        transcribing =
            true

        VoiceStatusState
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

        /*
         * Whisper gira in background.
         */
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

                                TextInsertionService
                                    .insert(
                                        capturedTarget,
                                        transcription
                                    )

                            } finally {

                                transcribing =
                                    false

                                VoiceStatusState
                                    .setReady()
                            }
                        }

                } catch (ex: Exception) {

                    ApplicationManager
                        .getApplication()
                        .invokeLater {

                            transcribing =
                                false

                            VoiceStatusState
                                .setReady()

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
}
