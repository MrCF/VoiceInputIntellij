# Voice Input

Local speech-to-text dictation for IntelliJ-based IDEs, powered by
[whisper.cpp](https://github.com/ggml-org/whisper.cpp).

Voice Input lets you dictate text directly at the current cursor position without sending your audio to an external speech-to-text service.

Audio recording and transcription are performed locally on your computer.

## Features

- 🎙 Local speech-to-text using whisper.cpp
- 🔒 No cloud speech-to-text API required
- ⌨️ Toggle dictation with `Meta+V`
- 🎤 Push-to-Talk directly from the IDE status bar
- ⏱ Automatic stop after configurable silence
- 🗣 Voice Activity Detection (VAD)
- 🧠 Context-aware Whisper prompting
- 💻 Automatic detection of technical identifiers from the current editor
- 📜 Transcription history
- 📋 Copy and re-insert previous transcriptions
- 🔴 Visible recording indicator and timer
- 🌍 Multilingual transcription through Whisper
- ⚙️ Configurable Whisper model and language

## How it works

Voice Input records microphone audio and sends it to a local whisper.cpp runtime.

The resulting transcription is inserted directly at the position where dictation was started.

No OpenAI API key or external speech-to-text service is required.

Voice Input can also inspect the surrounding editor context and use relevant technical identifiers as a Whisper prompt.

For example, identifiers such as:

```text
CustomerController
CustomerService
PostMapping
ResponseEntity
```

can help Whisper recognize project-specific technical vocabulary.

## Dictation modes

### Toggle dictation

The default shortcut is:

```text
Meta+V
```

Press once to start recording.

Press again to stop recording and start transcription.

When Automatic Stop is enabled, recording can also stop automatically after the configured amount of silence.

The shortcut can be changed from the IDE Keymap settings.

### Push-to-Talk

Voice Input provides a Push-to-Talk control in the IDE status bar.

Press and hold:

```text
🎙 Hold to talk
```

and speak while keeping the mouse button pressed.

The status bar changes to:

```text
● REC 00:03
```

Release the mouse button to stop recording and start transcription.

Automatic Stop is intentionally disabled while using Push-to-Talk. Recording continues for as long as the microphone control is held.

## Automatic Stop

Voice Input can automatically stop recording after detecting a configurable period of silence.

Silence detection uses an adaptive threshold based on the ambient noise level, allowing it to adjust to different microphone and room conditions.

Automatic Stop only becomes active after speech has been detected, so the recording will not stop simply because you wait before beginning to speak.

Automatic Stop does not apply to Push-to-Talk mode.

## Voice Activity Detection

Voice Activity Detection can be enabled from the Voice Input settings.

VAD can help Whisper distinguish speech from non-speech portions of a recording.

It can be enabled or disabled independently from Automatic Stop.

## Context-aware transcription

Voice Input can build a dynamic Whisper prompt using the current editor.

It collects useful identifiers from the surrounding source code and, when available, from the IntelliJ PSI structure.

This can improve recognition of names such as:

```text
UserService
CustomerRepository
CreateCustomerRequest
HttpStatus
ResponseEntity
```

A custom technical prompt can also be configured manually.

The context is used only as a local hint for Whisper and is not sent to an external service.

## Transcription History

Recent transcriptions are stored in the Voice Input History Tool Window.

The history keeps the most recent transcriptions for the current IDE session.

From the history you can:

- copy a transcription to the clipboard;
- insert it into the editor;
- double-click an entry to insert it;
- clear the history.

The history is collected even if the Tool Window has never been opened.

## Settings

Voice Input settings are available under:

```text
Settings
└── Tools
    └── Voice Input
```

Available options include settings for:

- Whisper model
- transcription language
- audio input device
- technical prompt
- Voice Activity Detection
- Automatic Stop
- silence duration

## Whisper models

Voice Input supports Tiny, Base, Small, Medium, Large v3 Turbo and Large v3
local Whisper models.

Different model sizes provide different trade-offs between transcription accuracy, processing speed and resource usage.

For example, smaller models can provide faster transcription, while larger models can improve recognition in more difficult speech or language conditions.

Model files are not stored in the Voice Input source repository.

## Privacy

Voice Input is designed around local speech processing.

Microphone audio is recorded locally and transcription is performed by the bundled whisper.cpp runtime using a local Whisper model.

Voice Input does not require an OpenAI API key and does not require sending recorded speech to a cloud speech-to-text API.

Editor context used for technical prompting is also processed locally.

## Current platform support

The current release bundles the whisper.cpp runtime for:

```text
Linux x86-64
```

Other operating systems and architectures are not currently included in the distributed runtime.

## IntelliJ compatibility

Voice Input `0.4.0` has been built and verified against:

```text
IntelliJ IDEA 2026.2.1
IU-262.9437.185
```

The IntelliJ Plugin Verifier reports the plugin as compatible with this IDE version.

## Building from source

Requirements:

- JDK compatible with the IntelliJ Platform Gradle Plugin
- Gradle wrapper included with the project

Build the plugin with:

```bash
./gradlew clean buildPlugin
```

The resulting plugin distribution is created under:

```text
build/distributions/
```

Run the IntelliJ Plugin Verifier with:

```bash
./gradlew verifyPlugin
```

For local development, launch the sandbox IDE with:

```bash
./gradlew runIde
```

## Third-party components

Voice Input includes native components from
[whisper.cpp](https://github.com/ggml-org/whisper.cpp), including the Whisper command-line runtime and supporting GGML libraries.

whisper.cpp is distributed under the MIT License.

See:

```text
THIRD_PARTY_NOTICES.md
```

and the bundled whisper.cpp license for additional information.

Whisper model files are separate from the Voice Input source code and remain subject to their respective licensing terms.

## License

Voice Input is released under the MIT License.

See:

```text
LICENSE
```

for the full license text.

Copyright © 2026 Federico Carpeggiani
