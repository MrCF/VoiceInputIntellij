# Changelog

All notable changes to Voice Input will be documented in this file.

## [0.4.2]

### Added

- English Processing selector with Automatic (recommended), CPU only and GPU modes.
- Background checks on first project startup and when opening settings, using the bundled backend to initialize a compatible Vulkan GPU.
- Greyed-out GPU selection when unavailable, an explanatory message below the selector, and a Check again button.
- Independent CPU runtime with portable and optimized processor backends; Vulkan is optional.

### Changed

- Automatic mode uses CPU when GPU requirements are missing and retries failed GPU transcriptions on CPU. CPU only avoids GPU transcription; GPU mode reports errors without silently falling back.
- Availability checks are cached between rechecks. A successful recheck clears an earlier automatic CPU fallback. CPU-only startup skips GPU detection until settings are opened.
- Models remain selectable independently of the processing mode.
- One Linux x86-64 ZIP includes CPU and optional Vulkan acceleration, with separate content-versioned extraction directories to avoid mixing native libraries during upgrades.

## [0.4.1]

### Added

- Punctuation settings: no final period (default), or no punctuation while preserving word apostrophes and decimal separators.
- Custom vector Voice Input plugin logo.

### Fixed

- Automatic final periods no longer interrupt dictation across multiple recordings.
- Inserted transcription blocks are separated from adjacent words where needed.

## [0.4.0]

### Added

- Configurable audio input device selection.
- Optional audio feedback when recording starts and stops.
- Additional Whisper model choices, including Large v3 Turbo and Large v3.
- Vulkan-enabled bundled whisper.cpp runtime for Linux x86-64.
- Localized Voice Input history messages and history service coverage.

### Changed

- Recording can start while earlier recordings are still being transcribed.
- Recordings are queued and inserted in their original recording order.
- Each queued recording uses an independent temporary audio snapshot.

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
