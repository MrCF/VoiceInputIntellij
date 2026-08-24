package it.federico.voiceinput

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.util.concurrency.SequentialTaskExecutor
import java.io.File
import java.nio.file.Files

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

    private val transcriptionExecutor =
        SequentialTaskExecutor
            .createSequentialApplicationPoolExecutor(
                "Voice Input transcription"
            )

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
            isRecording
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
        val capturedTarget =
            target

        val capturedEditor =
            contextEditor

        /*
         * I modelli dell'editor possono essere letti solo sull'EDT.
         * Il prompt viene costruito successivamente su un pooled thread,
         * quindi conserviamo ora un semplice snapshot dell'offset.
         */
        val capturedCaretOffset =
            capturedEditor
                ?.takeUnless { it.isDisposed }
                ?.caretModel
                ?.offset

        clearCapturedContext()

        if (capturedTarget == null) {
            VoiceSessionService
                .setIdle()

            return
        }

        val capturedAudioFile =
            try {
                snapshotRecordedAudio()
            } catch (ex: Exception) {
                VoiceSessionService.setIdle()
                Messages.showErrorDialog(
                    project,
                    ex.message ?: "Errore durante la preparazione dell'audio.",
                    "Voice Input"
                )
                return
            }

        VoiceSessionService.setTranscribing()

        val staticPrompt =
            VoiceSettings
                .getInstance()
                .state
                .prompt

        transcriptionExecutor.execute {

                var sessionFinished =
                    false

                try {

                    val whisperPrompt =
                        WhisperPromptService
                            .buildPrompt(
                                capturedEditor,
                                staticPrompt,
                                capturedCaretOffset
                            )

                    val transcription =
                        whisper.transcribe(
                            capturedAudioFile,
                            whisperPrompt
                        )

                    /*
                     * La coda non passa alla registrazione successiva finché
                     * questa trascrizione non è stata consegnata all'editor.
                     * In questo modo anche l'ordine di inserimento, non solo
                     * quello di esecuzione di Whisper, coincide con l'ordine
                     * delle registrazioni.
                     */
                    ApplicationManager
                        .getApplication()
                        .invokeAndWait {

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

                                sessionFinished =
                                    true

                                VoiceSessionService
                                    .transcriptionFinished()
                            }
                        }

                } catch (ex: Exception) {

                    ApplicationManager
                        .getApplication()
                        .invokeAndWait {

                            if (!sessionFinished) {
                                sessionFinished = true
                                VoiceSessionService.transcriptionFinished()
                            }

                            Messages.showErrorDialog(
                                project,
                                ex.message
                                    ?: "Errore durante la trascrizione.",
                                "Voice Input"
                            )
                        }
                } finally {
                    if (!sessionFinished) {
                        VoiceSessionService.transcriptionFinished()
                    }
                    capturedAudioFile.delete()
                }
            }
    }

    private fun snapshotRecordedAudio(): File {

        val snapshot =
            Files.createTempFile(
                "voice-input-",
                ".wav"
            ).toFile()

        return try {
            Files.copy(
                recorder.outputFile.toPath(),
                snapshot.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
            snapshot
        } catch (ex: Exception) {
            snapshot.delete()
            throw ex
        }
    }

    private fun clearCapturedContext() {

        target =
            null

        contextEditor =
            null
    }
}
