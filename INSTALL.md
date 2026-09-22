# Installing Voice Input on Linux

This guide covers `voice-input-0.4.2-linux-x86_64.zip`.

## Requirements

- Linux x86-64 (64-bit Intel or AMD). This ZIP does not support ARM, Windows or macOS.
- IntelliJ IDEA 2026.2 (build `262.*`). See [README.md](README.md#intellij-compatibility) for the verified IDE version.
- A working microphone accessible to the IDE through Java Sound and the system audio configuration.
- **Optional:** a Vulkan loader and compatible GPU driver for GPU acceleration. CPU transcription does not require Vulkan.
- The system C/C++, GCC and OpenMP runtime libraries listed below.
- Internet access to download a Whisper model initially, and enough disk space and RAM for the selected model. Subsequent transcription runs locally.

The CPU runtime requires **glibc 2.38 or newer** and a `libstdc++.so.6` providing **`GLIBCXX_3.4.29`**. The optional Vulkan runtime requires **`GLIBCXX_3.4.32`** (GCC 13.2 or newer runtime). These requirements
were checked in the bundled binaries.
They are necessary conditions, not a guarantee that every GPU or distribution is supported.

Ubuntu 24.04 LTS and Debian 13 provide sufficiently recent base libraries. Ubuntu 22.04 and Debian 12 do not satisfy these requirements with their standard base libraries. Use a newer distribution or a runtime
rebuilt for the older system; do not manually replace the system glibc.

## What is bundled

The ZIP includes two independent whisper.cpp runtimes: CPU and Vulkan. Each has its own executable and libraries, extracted into separate content-versioned directories. The CPU runtime includes a portable
x86-64 backend and optimized variants selected automatically for the processor. Keep the ZIP intact for installation.

The following system components are used; Vulkan components are optional:

| Component                         | Purpose                                                           |
|-----------------------------------|-------------------------------------------------------------------|
| `libvulkan.so.1`                  | Optional loader for GPU acceleration                              |
| Vulkan GPU driver (ICD)           | Optional driver for GPU acceleration                              |
| `libgomp.so.1`                    | OpenMP runtime                                                    |
| `libstdc++.so.6`, `libgcc_s.so.1` | C++ and GCC runtimes                                              |
| `libc.so.6`, `libm.so.6`          | System C and math libraries                                       |
| ALSA userspace library            | Audio access through the IDE's Java Sound implementation on Linux |

**Vulkan is not required for CPU transcription.** Choose a processing mode in **Settings → Tools → Voice Input**:

- **Automatic (recommended):** checks GPU availability in the background; uses CPU when Vulkan or a compatible GPU is unavailable. A failed GPU transcription is retried on CPU.
- **CPU only:** always uses the independent CPU runtime for transcription.
- **GPU:** available only after a successful GPU check. Transcription errors are reported without silently switching to CPU.

GPU detection runs in the background on first project startup and when opening settings. If **CPU only** is saved, startup skips the check until settings are opened. While a check is running, CPU options remain
available and GPU selection is disabled. Results are cached between checks, so transcription does not repeatedly probe the GPU.

The GPU option is greyed out when requirements are missing, with an explanation below the selector. Click **Check again** after installing Vulkan libraries or drivers. The check uses the bundled backend to
initialize a Vulkan GPU, without downloading a model or requiring `vulkaninfo`. It does not guarantee enough GPU memory for every model. A successful recheck clears an earlier automatic CPU fallback; driver
installation may still require a system restart.

All processing labels and status messages are in English. Whisper model choices remain available independently of CPU/GPU selection. The CPU thread setting applies to CPU computation, not to GPU utilization.

No Vulkan SDK, CUDA toolkit, separate Whisper installation, Python, FFmpeg or build toolchain is required. Use the Java runtime bundled with IntelliJ IDEA.

## Install system packages

Choose the section for your distribution. Install the base packages for CPU transcription. The GPU commands are optional and do not certify compatibility with every GPU.

### Ubuntu 24.04 LTS or Debian 13

```bash
sudo apt update
sudo apt install libgomp1 libstdc++6 libgcc-s1 libc6 libasound2t64
```

For optional GPU acceleration, install the loader and diagnostics:

```bash
sudo apt install libvulkan1 vulkan-tools
```

For supported Intel or AMD graphics, also install the Mesa Vulkan drivers:

```bash
sudo apt install mesa-vulkan-drivers
```

For NVIDIA, install the recommended proprietary driver for your GPU using Ubuntu's **Software & Updates → Additional Drivers** or
Debian's [NVIDIA driver instructions](https://wiki.debian.org/NvidiaGraphicsDrivers). The driver installation must include its Vulkan userspace components. Reboot after installing or changing the graphics
driver.

### Fedora (currently supported release)

```bash
sudo dnf install libgomp libstdc++ libgcc glibc alsa-lib
```

For optional GPU acceleration:

```bash
sudo dnf install vulkan-loader vulkan-tools
```

For supported Intel or AMD graphics:

```bash
sudo dnf install mesa-vulkan-drivers
```

For NVIDIA, follow the [RPM Fusion NVIDIA guide](https://rpmfusion.org/Howto/NVIDIA) for your GPU and Fedora release, including the Vulkan userspace components, then reboot.

### Arch Linux (fully updated system)

```bash
sudo pacman -Syu gcc-libs glibc alsa-lib
```

For optional GPU acceleration:

```bash
sudo pacman -S vulkan-icd-loader vulkan-tools
```

Install the Vulkan driver matching your GPU:

```bash
# Intel
sudo pacman -S vulkan-intel

# AMD
sudo pacman -S vulkan-radeon
```

For NVIDIA, follow the [Arch NVIDIA guide](https://wiki.archlinux.org/title/NVIDIA) to choose a compatible kernel driver and matching userspace package (`nvidia-utils` for the current driver series), then
reboot. Older GPUs may need a different driver branch.

## Verify Vulkan (optional)

Run as your normal desktop user:

```bash
vulkaninfo --summary
```

The output should list your physical GPU. A device named `llvmpipe` or `lavapipe` is a software implementation and does not establish that GPU acceleration works. A successful check confirms Vulkan device
discovery; the first transcription or plugin benchmark verifies that Whisper can actually use the device.

If the command cannot create a Vulkan instance or finds no devices, check that the driver supports your GPU and that its Vulkan components are installed. Restart the system after a driver change.

## Install the plugin ZIP

1. Open IntelliJ IDEA **Settings → Plugins**.
2. Open the gear menu and choose **Install Plugin from Disk…**.
3. Select `voice-input-0.4.2-linux-x86_64.zip` without extracting it.
4. Restart the IDE if prompted.
5. Open **Settings → Tools → Voice Input**.
6. Select and download a Whisper model. Start with Tiny or Base to check the installation before downloading a larger model.
7. Select the microphone and transcription language. Leave **Processing** on **Automatic (recommended)**, or choose **CPU only** to avoid GPU transcription.
8. If you enable Voice Activity Detection, download its separate VAD model in the same settings page.
9. Press `Meta+V` to start recording, speak, then press it again to transcribe. Alternatively, use the status bar Push-to-Talk control.

## Troubleshooting

### A native library is missing or too old

After a CPU fallback has extracted the runtime, locate the versioned `cpu` directory and inspect its dependencies:

```bash
find "$HOME/.local/share/voice-input/runtime/linux-x64" -path '*/cpu/whisper-cli'
# Replace <revision> with the directory printed above.
voice_runtime="$HOME/.local/share/voice-input/runtime/linux-x64/<revision>/cpu"
LD_LIBRARY_PATH="$voice_runtime${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" ldd "$voice_runtime/whisper-cli"
```

No dependency should say `not found`, and there should be no symbol-version errors. The library path is set only for this diagnostic command; the plugin sets it automatically when launching Whisper.

| Error                                            | Action                                                                                                                    |
|--------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `libvulkan.so.1: cannot open shared object file` | Automatic mode handles this with CPU processing. Install the loader only if GPU acceleration is wanted.                   |
| `libgomp.so.1: cannot open shared object file`   | Install OpenMP (`libgomp1`, `libgomp`, or Arch's `gcc-libs`).                                                             |
| `GLIBC_2.38 not found`                           | Use a distribution with glibc 2.38 or newer, or a compatible rebuilt runtime.                                             |
| `GLIBCXX_3.4.32 not found`                       | Update the distribution's C++ runtime; if unavailable, use a newer distribution or a compatible rebuilt runtime.          |
| Vulkan initialization or device errors           | Automatic mode handles this with CPU processing. To restore GPU acceleration, check the driver and click **Check again**. |

### Microphone is unavailable or silent

Check recording in the desktop audio settings, ensure the input is not muted, and select the correct device in Voice Input settings. The plugin records through Java Sound; it does not use an external recording
command. If the IDE is installed in a sandbox, ensure that it can access the microphone, GPU drivers and extracted native executable.

### Model or execution errors

Download the selected Whisper model before dictating, and the VAD model if VAD is enabled. Try a smaller model if memory is insufficient. The runtime directory under `~/.local/share/voice-input/` must be
writable, and its filesystem must allow executable files.

For further details, open the IDE log using **Help → Show Log in Files** (the exact label may vary) and look for the Whisper process error.
