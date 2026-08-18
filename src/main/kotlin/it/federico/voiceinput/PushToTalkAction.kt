package it.federico.voiceinput

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class PushToTalkAction :
    AnAction() {

    override fun actionPerformed(
        e: AnActionEvent
    ) {

        VoiceInputController
            .startPushToTalk(
                e
            )
    }
}
