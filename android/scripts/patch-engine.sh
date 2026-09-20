#!/usr/bin/env bash
# Neutralize BadukAI's libkatago.so package gate for our app.
# See android/docs/ON_DEVICE.md ("The gate, fully mapped").
#
# The gate: readlink(/proc/self/cwd) must start with /data/data/net.kir.baduk_ai
# AND be exactly 27 chars (b.ne fail at 0x37d9a0). One NOP removes the length
# requirement; the remaining prefix loop then only compares as many bytes as the
# cwd is long. Our cwd is /data/data (10 chars — every resolved app path on this
# device starts with it), so the original string passes untouched. Single patch:
#   0x37d9a0  b.ne fail  ->  NOP
#
# Usage: ./scripts/patch-engine.sh <base-libkatago.so> <out-libkatago.so>
set -euo pipefail
BASE="${1:?usage: patch-engine.sh <base.so> <out.so>}"
OUT="${2:?usage: patch-engine.sh <base.so> <out.so>}"
python3 - "$BASE" "$OUT" <<'EOF'
import struct, sys
base, out = sys.argv[1], sys.argv[2]
d = bytearray(open(base, 'rb').read())
assert len(d) == 5315424, f'unexpected size {len(d)} (want BadukAI v1.20.13 5315424B)'
old = b'/data/data/net.kir.baduk_ai'
assert d.count(old) == 1, f'check string occurs {d.count(old)}x, want exactly 1'
br = struct.unpack('<I', d[0x37d9a0:0x37d9a4])[0]
assert br == 0x54000201, f'branch changed: 0x{br:08x} (want 0x54000201)'
struct.pack_into('<I', d, 0x37d9a0, 0xD503201F)  # nop
assert len(d) == 5315424
open(out, 'wb').write(d)
print(f'patched ok: {out} ({len(d)}B)')
EOF
