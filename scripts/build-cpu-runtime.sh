#!/usr/bin/env bash
# Rebuild the optional-Vulkan distribution's independent Linux x86-64 CPU runtime.
set -euo pipefail
project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
build_root="${1:-$(mktemp -d -t voice-input-cpu.XXXXXX)}"
source_commit=f049fff95a089aa9969deb009cdd4892b3e74916
mkdir -p "$build_root"
if [[ ! -d "$build_root/source/.git" ]]; then
    git clone --depth 1 --branch v1.9.1 https://github.com/ggml-org/whisper.cpp.git "$build_root/source"
fi
[[ "$(git -C "$build_root/source" rev-parse HEAD)" == "$source_commit" ]]
cmake -S "$build_root/source" -B "$build_root/build" \
    -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON -DCMAKE_BUILD_RPATH_USE_ORIGIN=ON \
    -DGGML_BACKEND_DL=ON -DGGML_CPU_ALL_VARIANTS=ON -DGGML_NATIVE=OFF \
    -DGGML_VULKAN=OFF -DGGML_CUDA=OFF -DWHISPER_BUILD_TESTS=OFF \
    -DWHISPER_CURL=OFF -DWHISPER_SDL2=OFF
cmake --build "$build_root/build" --target whisper-cli --parallel "${VOICE_INPUT_BUILD_JOBS:-4}"
python3 "$project_root/scripts/package-cpu-runtime.py" "$build_root/build/bin"
