package it.federico.voiceinput

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(
    name = "VoiceInputSettings",
    storages = [Storage("VoiceInput.xml")]
)
class VoiceSettings :
    PersistentStateComponent<VoiceSettings.State> {

    data class State(

        var modelId: String =
            "small",

        var language: String =
            "it",

        var inputDeviceId: String =
            AudioInputManager.DEFAULT_DEVICE_ID,

        var threads: Int =
            ThreadConfig.recommended,

        var processingMode: ProcessingMode =
            ProcessingMode.AUTOMATIC,

        var punctuationMode: PunctuationMode =
            PunctuationMode.WITHOUT_FINAL_PERIOD,

        var prompt: String =
            "Java, Spring Boot, REST, JPA, Optional, " +
                    "ResponseEntity, HttpStatus, " +
                    "PostMapping, GetMapping, PutMapping, " +
                    "DeleteMapping, RequestBody, PathVariable",

        /*
         * VOICE ACTIVITY DETECTION
         */

        var vadEnabled: Boolean =
            false,

        var vadThreshold: Double =
            0.50,

        var vadMinSpeechDurationMs: Int =
            250,

        var vadMinSilenceDurationMs: Int =
            700,

        var vadSpeechPadMs: Int =
            250,

        var vadSamplesOverlapSeconds: Double =
            0.10,

        /*
         * AUTOMATIC STOP
         */

        var autoStopEnabled: Boolean =
            false,

        var autoStopSilenceSeconds: Double =
            3.0,

        /*
         * RECORDING AUDIO FEEDBACK
         */

        var recordingAudioFeedbackEnabled: Boolean =
            true
    )

    private var state =
        State()

    override fun getState(): State {
        return state
    }

    override fun loadState(
        state: State
    ) {

        /*
         * THREADS
         */
        state.threads =
            state.threads.coerceIn(
                ThreadConfig.minimum,
                ThreadConfig.maximum
            )

        /*
         * VAD
         */
        state.vadThreshold =
            state.vadThreshold.coerceIn(
                0.0,
                1.0
            )

        state.vadMinSpeechDurationMs =
            state.vadMinSpeechDurationMs
                .coerceIn(
                    0,
                    10_000
                )

        state.vadMinSilenceDurationMs =
            state.vadMinSilenceDurationMs
                .coerceIn(
                    0,
                    10_000
                )

        state.vadSpeechPadMs =
            state.vadSpeechPadMs
                .coerceIn(
                    0,
                    5_000
                )

        state.vadSamplesOverlapSeconds =
            state.vadSamplesOverlapSeconds
                .coerceIn(
                    0.0,
                    5.0
                )

        /*
         * AUTOMATIC STOP
         */
        state.autoStopSilenceSeconds =
            state.autoStopSilenceSeconds
                .coerceIn(
                    1.5,
                    10.0
                )

        this.state =
            state
    }

    companion object {

        fun getInstance():
                VoiceSettings {

            return ApplicationManager
                .getApplication()
                .getService(
                    VoiceSettings::class.java
                )
        }
    }
}
