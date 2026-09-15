# Installing Voice Input on Linux

This guide covers `voice-input-0.4.0-linux-x86_64-vulkan.zip`.

## Requirements

- Linux x86-64 (64-bit Intel or AMD). This ZIP does not support ARM, Windows or macOS.
- IntelliJ IDEA 2026.2 (build `262.*`). See [README.md](README.md#intellij-compatibility) for the verified IDE version.
- A working microphone accessible to the IDE through Java Sound and the system audio configuration.
- A Vulkan loader and a compatible GPU driver for GPU acceleration.
- The system C/C++, GCC and OpenMP runtime libraries listed below.
- Internet access to download a Whisper model initially, and enough disk space and RAM for the selected model. Subsequent transcription runs locally.

The native binaries in this ZIP require **glibc 2.38 or newer** and a `libstdc++.so.6` providing **`GLIBCXX_3.4.32`** (GCC 13.2 or newer runtime). These requirements were checked in the binaries inside the ZIP.
They are necessary conditions, not a guarantee that every GPU or distribution is supported.

Ubuntu 24.04 LTS and Debian 13 provide sufficiently recent base libraries. Ubuntu 22.04 and Debian 12 do not satisfy these requirements with their standard base libraries. Use a newer distribution or a runtime
rebuilt for the older system; do not manually replace the system glibc.

## What is bundled

The ZIP includes `whisper-cli`, `libwhisper.so.1`, and the GGML base, CPU and Vulkan libraries. The plugin extracts them automatically; keep the ZIP intact for installation.

The following components must come from the operating system:

| Component                         | Purpose                                                           |
|-----------------------------------|-------------------------------------------------------------------|
| `libvulkan.so.1`                  | Vulkan loader used by the bundled `libggml-vulkan.so.0`           |
| Vulkan GPU driver (ICD)           | Connects Vulkan to the installed GPU                              |
| `libgomp.so.1`                    | OpenMP runtime                                                    |
| `libstdc++.so.6`, `libgcc_s.so.1` | C++ and GCC runtimes                                              |
| `libc.so.6`, `libm.so.6`          | System C and math libraries                                       |
| ALSA userspace library            | Audio access through the IDE's Java Sound implementation on Linux |

This build links to Vulkan through its native library dependencies: **the Vulkan loader is required even when transcription uses the CPU**. The bundled CPU library does not make this a standalone CPU-only
distribution.

No Vulkan SDK, CUDA toolkit, separate Whisper installation, Python, FFmpeg or build toolchain is required. Use the Java runtime bundled with IntelliJ IDEA.

## Install system packages

Choose the section for your distribution. Commands install 64-bit runtime packages and the optional `vulkaninfo` diagnostic utility. They do not certify compatibility with every GPU.

### Ubuntu 24.04 LTS or Debian 13

```bash
sudo apt update
sudo apt install libvulkan1 vulkan-tools libgomp1 libstdc++6 libgcc-s1 libc6 libasound2t64
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
sudo dnf install vulkan-loader vulkan-tools libgomp libstdc++ libgcc glibc alsa-lib
```

For supported Intel or AMD graphics:

```bash
sudo dnf install mesa-vulkan-drivers
```

For NVIDIA, follow the [RPM Fusion NVIDIA guide](https://rpmfusion.org/Howto/NVIDIA) for your GPU and Fedora release, including the Vulkan userspace components, then reboot.

### Arch Linux (fully updated system)

```bash
sudo pacman -Syu vulkan-icd-loader vulkan-tools gcc-libs glibc alsa-lib
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

## Verify Vulkan

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
3. Select `voice-input-0.4.0-linux-x86_64-vulkan.zip` without extracting it.
4. Restart the IDE if prompted.
5. Open **Settings → Tools → Voice Input**.
6. Select and download a Whisper model. Start with Tiny or Base to check the installation before downloading a larger model.
7. Select the microphone and transcription language.
8. If you enable Voice Activity Detection, download its separate VAD model in the same settings page.
9. Press `Meta+V` to start recording, speak, then press it again to transcribe. Alternatively, use the status bar Push-to-Talk control.

## Troubleshooting

### A native library is missing or too old

After the first transcription attempt has extracted the runtime, inspect its dependencies:

```bash
voice_runtime="$HOME/.local/share/voice-input/runtime/linux-x64"
LD_LIBRARY_PATH="$voice_runtime${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" ldd "$voice_runtime/whisper-cli"
```

No dependency should say `not found`, and there should be no symbol-version errors. The library path is set only for this diagnostic command; the plugin sets it automatically when launching Whisper.

| Error                                            | Action                                                                                                           |
|--------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| `libvulkan.so.1: cannot open shared object file` | Install the Vulkan loader package for your distribution.                                                         |
| `libgomp.so.1: cannot open shared object file`   | Install OpenMP (`libgomp1`, `libgomp`, or Arch's `gcc-libs`).                                                    |
| `GLIBC_2.38 not found`                           | Use a distribution with glibc 2.38 or newer, or a compatible rebuilt runtime.                                    |
| `GLIBCXX_3.4.32 not found`                       | Update the distribution's C++ runtime; if unavailable, use a newer distribution or a compatible rebuilt runtime. |
| Vulkan initialization or device errors           | Run `vulkaninfo --summary` and check the GPU driver.                                                             |

### Microphone is unavailable or silent

Check recording in the desktop audio settings, ensure the input is not muted, and select the correct device in Voice Input settings. The plugin records through Java Sound; it does not use an external recording
command. If the IDE is installed in a sandbox, ensure that it can access the microphone, GPU drivers and extracted native executable.

### Model or execution errors

Download the selected Whisper model before dictating, and the VAD model if VAD is enabled. Try a smaller model if memory is insufficient. The runtime directory under `~/.local/share/voice-input/` must be
writable, and its filesystem must allow executable files.

For further details, open the IDE log using **Help → Show Log in Files** (the exact label may vary) and look for the Whisper process error.
