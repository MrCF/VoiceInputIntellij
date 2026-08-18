import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

group = "it.federico.voiceinput"
version = "0.1.0"

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
                <li>Initial 0.1.0 release</li>
                <li>Local Whisper speech-to-text</li>
                <li>Small, Medium and Large v3 Turbo model support</li>
                <li>Guided CPU thread configuration</li>
                <li>Natural and technical speech benchmark</li>
                <li>Project-aware technical prompt generation</li>
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
