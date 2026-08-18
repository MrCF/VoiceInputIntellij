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
        get() =
            currentState ==
                    State.RECORDING

    val isTranscribing: Boolean
        get() =
            currentState ==
                    State.TRANSCRIBING

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

    fun setRecording() {

        recordingStartedAt =
            System.nanoTime()

        setState(
            State.RECORDING
        )

        VoiceStatusState
            .setRecording()
    }

    fun setTranscribing() {

        setState(
            State.TRANSCRIBING
        )

        VoiceStatusState
            .setTranscribing()
    }

    fun setIdle() {

        recordingStartedAt =
            0L

        setState(
            State.IDLE
        )

        VoiceStatusState
            .setReady()
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
