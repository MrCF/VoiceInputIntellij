import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

group = "it.federico.voiceinput"

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
                <li>Added Automatic (recommended), CPU only and GPU processing modes</li>
                <li>GPU availability is checked in the background on startup and when opening settings; unavailable GPU options are greyed out with an explanation</li>
                <li>Added Check again to detect installed Vulkan libraries and drivers and retry GPU acceleration</li>
                <li>Bundled an independent CPU runtime, making Vulkan optional for transcription</li>
                <li>Automatic mode uses CPU when GPU requirements are missing and retries failed GPU transcriptions on CPU; GPU mode reports errors without silently falling back</li>
                <li>Models remain available independently of the selected processing mode</li>
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

tasks.buildPlugin {
    archiveClassifier.set("linux-x86_64")
}
