#!/usr/bin/env python3
"""Package a CPU build and regenerate the content-addressed native runtime manifest."""
import hashlib
import shutil
import subprocess
import sys
from pathlib import Path

source = Path(sys.argv[1]).resolve()
root = Path(__file__).resolve().parent.parent / 'src/main/resources/runtime/linux-x64'
names = ['whisper-cli', 'libwhisper.so.1', 'libggml.so.0', 'libggml-base.so.0']
names += sorted(p.name for p in source.glob('libggml-cpu-*.so'))
assert 'libggml-cpu-x64.so' in names, 'Missing portable CPU backend'
for name in names:
    deps = subprocess.check_output(['readelf', '-d', str(source / name)], text=True)
    assert 'libvulkan' not in deps.lower(), f'Unexpected Vulkan dependency: {name}'
(root / 'cpu').mkdir(exist_ok=True)
for name in names:
    shutil.copyfile(source / name, root / 'cpu' / name)
    subprocess.run(['strip', '--strip-unneeded', str(root / 'cpu' / name)], check=True)
(root / 'cpu' / 'whisper-cli').chmod(0o755)
for obsolete in (root / 'cpu').iterdir():
    if obsolete.name not in names:
        obsolete.unlink()
vulkan = ['whisper-cli', 'libwhisper.so.1', 'libggml.so.0',
          'libggml-base.so.0', 'libggml-cpu.so.0', 'libggml-vulkan.so.0']
paths = vulkan + ['cpu/' + name for name in names]
if (root / 'gpu-probe').is_file():
    paths.append('gpu-probe')
(root / 'SHA256SUMS').write_text(''.join(
    hashlib.sha256((root / name).read_bytes()).hexdigest() + '  ' + name + '\n'
    for name in paths
))
print(f'Packaged {len(names)} CPU runtime files; regenerated SHA256SUMS')
