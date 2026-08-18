# Changelog

All notable changes to Voice Input will be documented in this file.

## [0.2.1]

### Improved

- Improved automatic stop reliability with adaptive noise detection.
- Replaced the fixed silence threshold with automatic background-noise estimation.
- Silence detection now adapts to different microphones and ambient noise levels.
- Added a minimum threshold to prevent excessive sensitivity in very quiet environments.
- Background-noise estimation updates gradually without following the user's voice.

## [0.2.0]

### Added

- Voice Activity Detection (VAD) support using Silero VAD.
- Automatic recording stop after a configurable period of silence.
- Configurable automatic-stop timeout.
- Configurable VAD parameters:
  - Threshold
  - Minimum speech duration
  - Minimum silence duration
  - Speech padding
  - Sample overlap
- VAD model download and removal from Voice Input settings.
- Download progress displayed directly in the Settings window.
- Manual `Meta+V` stop remains available when automatic stop is enabled.

### Changed

- Automatic stop and Whisper VAD are independent features.
- Recording can wait indefinitely for the user to start speaking without triggering automatic stop.
- Manual and automatic recording stops use the same transcription pipeline.

## [0.1.0]

### Added

- Voice dictation directly at the current caret position.
- `Meta+V` shortcut to start and stop recording.
- Local transcription using whisper.cpp.
- Bundled Linux x64 whisper.cpp runtime.
- Automatic Whisper model download and management.
- Support for multiple Whisper models.
- Language selection.
- Configurable CPU thread count based on available processors.
- Technical prompt support for programming terminology.
- Editor-context-aware Whisper prompt.
- Built-in transcription benchmark.
- Language-specific benchmark sentences.
- Voice Input status indicator in the IDE status bar.
- Whisper model download progress in Settings.
- Local processing with no external speech-to-text API required.
