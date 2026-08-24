import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

group = "it.federico.voiceinput"
version = "0.4.0"

dependencies {
    testImplementation(libs.junit)

    intellijPlatform {
        intellijIdea("2026.2")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "it.federico.voiceinput"
        name = "Voice Input"
        version = project.version.toString()

        description = """
            <p>
            Voice Input adds local speech-to-text dictation to IntelliJ IDEA.
            </p>

            <p>
            Press <b>Meta+V</b> to start recording and press it again to
            transcribe your speech directly into the active editor, AI chat,
            or supported text field.
            </p>

            <p>
            Speech recognition runs locally using whisper.cpp.
            No external speech API or API key is required.
            </p>

            <ul>
                <li>Local speech recognition</li>
                <li>Fast, Balanced and Quality recognition modes</li>
                <li>Multiple languages</li>
                <li>Project-aware technical vocabulary</li>
                <li>Built-in model download and benchmark</li>
                <li>Configurable keyboard shortcut</li>
            </ul>
        """.trimIndent()

        changeNotes = """
            <ul>
                <li>Added recording overlay with live REC timer</li>
                <li>Added in-memory transcription history</li>
                <li>Added Copy, Insert and Clear actions to Voice Input History</li>
                <li>Added graphical Push-to-Talk control in the status bar</li>
                <li>Push-to-Talk ignores automatic silence stop while held</li>
                <li>Improved automatic stop with adaptive background-noise detection</li>
                <li>Improved Whisper technical prompting to avoid excessive punctuation</li>
                <li>Added Voice Activity Detection support</li>
            </ul>
        """.trimIndent()

        ideaVersion {
            sinceBuild = "262"
            untilBuild = "262.*"
        }

        vendor {
            name = "Federico"
        }
    }
}
