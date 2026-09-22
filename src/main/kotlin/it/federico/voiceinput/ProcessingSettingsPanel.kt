package it.federico.voiceinput

import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.*

internal class ProcessingModeModel : DefaultComboBoxModel<ProcessingMode>(ProcessingMode.entries.toTypedArray()) {
    var gpuAvailable = false
    override fun setSelectedItem(item: Any?) {
        if (item != ProcessingMode.GPU || gpuAvailable) super.setSelectedItem(item)
    }

    // Preserve a saved preference while checking, even if this machine no longer supports it.
    fun restore(mode: ProcessingMode) {
        super.setSelectedItem(mode)
    }
}

internal class ProcessingSettingsPanel : JPanel(BorderLayout(0, 6)) {
    private val modes = ProcessingModeModel()
    private val selector = JComboBox(modes)
    private val message = JBLabel()
    private val retry = JButton("Check again")
    private var listening = false
    private var controlsEnabled = true
    private var availability = GpuAvailability.CHECKING
    private val service get() = GpuAvailabilityService.getInstance()
    private val listener: (GpuAvailability) -> Unit = { status ->
        SwingUtilities.invokeLater { if (listening) update(status) }
    }

    var selectedMode: ProcessingMode
        get() = selector.selectedItem as ProcessingMode
        set(value) {
            modes.restore(value)
        }

    init {
        selector.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int,
                isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
                    isEnabled = selector.isEnabled && (value != ProcessingMode.GPU || modes.gpuAvailable)
                }
            }
        }
        selector.toolTipText = "Automatic uses GPU when available and falls back to CPU. " +
                "CPU only never uses GPU for transcription. GPU requires a successful availability check."
        add(JPanel(BorderLayout(8, 0)).apply {
            add(selector, BorderLayout.CENTER)
            add(retry, BorderLayout.EAST)
        }, BorderLayout.NORTH)
        add(message, BorderLayout.CENTER)
        retry.addActionListener { service.check(force = true) }
        update(availability)
    }

    fun start() {
        if (!listening) {
            listening = true
            service.addListener(listener)
        }
        update(service.current)
        service.check(force = true)
    }

    fun dispose() {
        if (listening) {
            listening = false
            service.removeListener(listener)
        }
    }

    fun setControlsEnabled(enabled: Boolean) {
        controlsEnabled = enabled
        selector.isEnabled = enabled
        retry.isEnabled = enabled && availability.checked
    }

    private fun update(status: GpuAvailability) {
        availability = status
        modes.gpuAvailable = status.available
        message.text = "<html><div style='width: 420px'>${StringUtil.escapeXmlEntities(status.message)}</div></html>"
        retry.isEnabled = controlsEnabled && status.checked
        selector.repaint()
    }
}
