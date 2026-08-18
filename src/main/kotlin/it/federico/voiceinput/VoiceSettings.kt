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

        var threads: Int =
            ThreadConfig.recommended,

        var prompt: String =
            "Java, Spring Boot, REST, JPA, Optional, " +
                    "ResponseEntity, HttpStatus, " +
                    "PostMapping, GetMapping, PutMapping, " +
                    "DeleteMapping, RequestBody, PathVariable"
    )

    private var state =
        State()

    override fun getState(): State {
        return state
    }

    override fun loadState(state: State) {

        /*
         * Se le impostazioni arrivano da una macchina diversa
         * o da una configurazione precedente, evitiamo valori
         * fuori dal range disponibile.
         */
        state.threads =
            state.threads.coerceIn(
                ThreadConfig.minimum,
                ThreadConfig.maximum
            )

        this.state =
            state
    }

    companion object {

        fun getInstance(): VoiceSettings {

            return ApplicationManager
                .getApplication()
                .getService(
                    VoiceSettings::class.java
                )
        }
    }
}
