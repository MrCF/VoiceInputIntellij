package it.federico.voiceinput

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.Mixer
import javax.sound.sampled.TargetDataLine

object AudioInputManager {

    const val DEFAULT_DEVICE_ID = ""

    data class Device(
        val id: String,
        val displayName: String
    ) {
        override fun toString(): String = displayName
    }

    fun devices(format: AudioFormat): List<Device> {
        val lineInfo = DataLine.Info(TargetDataLine::class.java, format)

        return listOf(Device(DEFAULT_DEVICE_ID, "System default")) +
                AudioSystem.getMixerInfo()
                    .asSequence()
                    .filter { AudioSystem.getMixer(it).isLineSupported(lineInfo) }
                    .map { info -> Device(deviceId(info), info.name) }
                    .distinctBy { it.id }
                    .toList()
    }

    fun openLine(format: AudioFormat, deviceId: String): TargetDataLine {
        val lineInfo = DataLine.Info(TargetDataLine::class.java, format)

        if (deviceId.isBlank()) {
            return AudioSystem.getLine(lineInfo) as TargetDataLine
        }

        val mixerInfo = AudioSystem.getMixerInfo()
            .firstOrNull { deviceId(it) == deviceId }
            ?: throw IllegalStateException(
                "The selected audio input device is no longer available."
            )

        return AudioSystem.getMixer(mixerInfo).getLine(lineInfo) as TargetDataLine
    }

    private fun deviceId(info: Mixer.Info): String =
        listOf(info.name, info.vendor, info.description, info.version)
            .joinToString("\u001f")
}
