package it.federico.voiceinput

import java.util.concurrent.CopyOnWriteArrayList

object VoiceSessionService {

    enum class State {
        IDLE,
        RECORDING,
        TRANSCRIBING
    }

    interface Listener {

        fun stateChanged(
            state: State
        )

        fun transcriptionCompleted(
            text: String
        ) {
            // opzionale
        }
    }

    private val listeners =
        CopyOnWriteArrayList<Listener>()

    @Volatile
    private var currentState =
        State.IDLE

    private var recording =
        false

    private var activeTranscriptions =
        0

    @Volatile
    private var recordingStartedAt =
        0L

    val state: State
        get() =
            currentState

    val isIdle: Boolean
        get() =
            currentState ==
                    State.IDLE

    val isRecording: Boolean
        @Synchronized get() = recording

    val isTranscribing: Boolean
        @Synchronized get() = activeTranscriptions > 0

    /*
     * Durata della registrazione corrente.
     *
     * È espressa in millisecondi.
     */
    val recordingDurationMs: Long
        get() {

            if (
                currentState !=
                State.RECORDING
            ) {
                return 0L
            }

            return (
                    System.nanoTime() -
                            recordingStartedAt
                    ) /
                    1_000_000L
        }

    @Synchronized
    fun setRecording() {

        recordingStartedAt =
            System.nanoTime()

        recording =
            true

        publishCurrentState()
    }

    @Synchronized
    fun setTranscribing() {

        recording =
            false

        recordingStartedAt =
            0L

        activeTranscriptions++

        publishCurrentState()
    }

    @Synchronized
    fun setIdle() {

        recording =
            false

        recordingStartedAt =
            0L

        publishCurrentState()
    }

    @Synchronized
    fun transcriptionFinished() {

        if (activeTranscriptions > 0) {
            activeTranscriptions--
        }

        publishCurrentState()
    }

    private fun publishCurrentState() {

        val newState =
            when {
                recording -> State.RECORDING
                activeTranscriptions > 0 -> State.TRANSCRIBING
                else -> State.IDLE
            }

        setState(newState)

        when (newState) {
            State.RECORDING -> VoiceStatusState.setRecording()
            State.TRANSCRIBING -> VoiceStatusState.setTranscribing()
            State.IDLE -> VoiceStatusState.setReady()
        }
    }

    fun transcriptionCompleted(
        text: String
    ) {

        listeners.forEach { listener ->

            try {

                listener
                    .transcriptionCompleted(
                        text
                    )

            } catch (_: Exception) {
                /*
                 * Un listener non deve mai
                 * interrompere la pipeline vocale.
                 */
            }
        }
    }

    fun addListener(
        listener: Listener
    ) {

        listeners.addIfAbsent(
            listener
        )
    }

    fun removeListener(
        listener: Listener
    ) {

        listeners.remove(
            listener
        )
    }

    private fun setState(
        newState: State
    ) {

        if (
            currentState ==
            newState
        ) {
            return
        }

        currentState =
            newState

        listeners.forEach { listener ->

            try {

                listener.stateChanged(
                    newState
                )

            } catch (_: Exception) {
                /*
                 * Come sopra: un problema
                 * nell'overlay/history non deve
                 * rompere Voice Input.
                 */
            }
        }
    }
}
