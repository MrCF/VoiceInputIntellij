package it.federico.voiceinput

import com.intellij.openapi.wm.StatusBar

object VoiceStatusState {

    var text: String = "Voice Input: Ready"

    var statusBar: StatusBar? = null

    fun setReady() {
        text = "Voice Input: Ready"
        refresh()
    }

    fun setRecording() {
        text = "🎙 Voice Input: REC"
        refresh()
    }

    fun setTranscribing() {
        text = "Voice Input: Transcribing..."
        refresh()
    }

    private fun refresh() {
        statusBar?.updateWidget("VoiceInputStatus")
    }
}
