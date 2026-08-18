package it.federico.voiceinput

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Consumer
import java.awt.event.MouseEvent

class VoiceStatusWidgetFactory : StatusBarWidgetFactory {

    companion object {
        const val WIDGET_ID = "VoiceInputStatus"
    }

    override fun getId(): String =
        WIDGET_ID

    override fun getDisplayName(): String =
        "Voice Input"

    override fun isAvailable(project: Project): Boolean =
        true

    override fun createWidget(project: Project): StatusBarWidget =
        VoiceStatusWidget()

    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean =
        true
}

class VoiceStatusWidget :
    StatusBarWidget,
    StatusBarWidget.TextPresentation {

    override fun ID(): String =
        VoiceStatusWidgetFactory.WIDGET_ID

    override fun getPresentation():
            StatusBarWidget.WidgetPresentation =
        this

    override fun getText(): String =
        VoiceStatusState.text

    override fun getTooltipText(): String =
        "Voice Input"

    override fun getAlignment(): Float =
        0f

    override fun getClickConsumer():
            Consumer<MouseEvent>? =
        null

    override fun install(statusBar: StatusBar) {
        VoiceStatusState.statusBar =
            statusBar
    }

    override fun dispose() {

        if (
            VoiceStatusState.statusBar !=
            null
        ) {
            VoiceStatusState.statusBar =
                null
        }
    }
}
