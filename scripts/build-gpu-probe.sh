#!/usr/bin/env bash
# Argument: checkout of whisper.cpp v1.9.1, as used by build-cpu-runtime.sh.
set -euo pipefail
project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
source_root="${1:?Provide the whisper.cpp v1.9.1 source directory}"
runtime_root="$project_root/src/main/resources/runtime/linux-x64"
[[ "$(git -C "$source_root" rev-parse HEAD)" == f049fff95a089aa9969deb009cdd4892b3e74916 ]]
gcc -O2 -I "$source_root/ggml/include" "$project_root/native/gpu-probe.c" \
    -L "$runtime_root" -Wl,-rpath-link,"$runtime_root" \
    -Wl,-rpath,'$ORIGIN' -l:libggml.so.0 -l:libggml-base.so.0 \
    -o "$runtime_root/gpu-probe"
strip --strip-unneeded "$runtime_root/gpu-probe"
python3 - "$runtime_root" <<'PY'
import hashlib,sys
from pathlib import Path
root=Path(sys.argv[1])
paths=[line.split('  ')[1] for line in (root/'SHA256SUMS').read_text().splitlines()]
if 'gpu-probe' not in paths: paths.append('gpu-probe')
(root/'SHA256SUMS').write_text(''.join(hashlib.sha256((root/p).read_bytes()).hexdigest()+'  '+p+'\n' for p in paths))
PY
