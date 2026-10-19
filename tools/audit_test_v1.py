with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    orig = f.read()

with open('candidate/test_v1.0.ufw', 'rb') as f:
    ufw = f.read()

import sys, os
sys.path.insert(0, 'scripts/jieli_tools')
from jltech.cipher import jl_sfc_cipher

flash = bytearray(ufw[0x400:0x400+0x34000])
jl_sfc_cipher(flash, 0x2000, 0x32000, 0x2000, key=0x190D)
mod = flash[0x2120:0x2120+198824]

diffs = []
i = 0
while i < len(orig):
    if orig[i] != mod[i]:
        s = i
        while i < len(orig) and orig[i] != mod[i]:
            i += 1
        diffs.append((s, i-s))
    else:
        i += 1

print(f'Total diff chunks: {len(diffs)}')
for idx, (s, l) in enumerate(diffs):
    o_hex = orig[s:s+l].hex(' ')
    m_hex = mod[s:s+l].hex(' ')
    print(f"Chunk {idx+1}: app.bin off 0x{s:05X} (CPU: 0x{0x01E00120+s:08X}, len {l})")
    print(f"  Orig: {o_hex}")
    print(f"  Mod:  {m_hex}")
