#!/usr/bin/env python3
"""Test CPU transcription while denying Vulkan loading, and compare warm CPU latency.
Usage: python3 scripts/check-cpu-runtime.py /path/to/ggml-tiny.en.bin /path/to/jfk.wav
Requires Linux, gcc, the bundled runtime's C/C++ libraries, and a local model/audio sample.
"""
import argparse
import os
import statistics
import subprocess
import tempfile
import time
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('model', type=Path)
parser.add_argument('audio', type=Path)
parser.add_argument('--threads', type=int, default=4)
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent / 'src/main/resources/runtime/linux-x64'
command = ['-m', str(args.model.resolve()), '-f', str(args.audio.resolve()),
           '-l', 'en', '-t', str(args.threads), '--no-gpu', '--no-timestamps', '--no-prints']
with tempfile.TemporaryDirectory(prefix='voice-input-cpu-check-') as temporary:
    directory = Path(temporary)
    source = directory / 'block-vulkan.c'
    audit = directory / 'block-vulkan.so'
    source.write_text('''#define _GNU_SOURCE
#include <link.h>
#include <string.h>
#include <stdio.h>
unsigned int la_version(unsigned int version) { return LAV_CURRENT; }
char *la_objsearch(const char *name, uintptr_t *cookie, unsigned int flag) {
    if (strstr(name, "libvulkan")) {
        fprintf(stderr, "Blocked Vulkan: %s\\n", name);
        return 0;
    }
    return (char *)name;
}
''')
    subprocess.run(['gcc', '-shared', '-fPIC', '-o', str(audit), str(source)], check=True)


    def environment(path, blocked):
        env = dict(os.environ, LD_LIBRARY_PATH=str(path))
        env.pop('GGML_BACKEND_PATH', None)
        if blocked:
            env['LD_AUDIT'] = str(audit)
        return env


    blocked = subprocess.run([str(root / 'whisper-cli'), '--version'],
                             env=environment(root, True), capture_output=True, text=True, timeout=30)
    assert blocked.returncode != 0 and 'Blocked Vulkan:' in blocked.stderr, blocked
    print('Confirmed: existing Vulkan runtime cannot launch when Vulkan loading is denied.', flush=True)

    outputs = []
    for label, path, blocked in [('Original runtime, CPU mode', root, False),
                                 ('Independent CPU runtime', root / 'cpu', False),
                                 ('Independent CPU, Vulkan blocked', root / 'cpu', True)]:
        timings = []
        for iteration in range(4):  # One warm-up, then three measured repetitions.
            start = time.perf_counter()
            result = subprocess.run([str(path / 'whisper-cli'), *command], cwd=path,
                                    env=environment(path, blocked), capture_output=True,
                                    text=True, check=True, timeout=300)
            elapsed = time.perf_counter() - start
            if iteration:
                timings.append(elapsed)
        outputs.append(result.stdout.strip())
        assert outputs[-1], f'Empty transcription: {label}'
        print(f'{label}: median {statistics.median(timings):.3f} s; runs {timings}', flush=True)
    assert outputs[1] == outputs[2], 'Blocking Vulkan changed CPU transcription'
    print('CPU output with and without Vulkan is identical. Original CPU output matches:', outputs[0] == outputs[1])
    print(outputs[-1])
