import sys
sys.path.insert(0, 'scripts/jieli_tools')
from jltech.cipher import jl_sfc_cipher

with open('extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw_unpack/files/app.bin', 'rb') as f:
    orig_app = f.read()

with open('kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.0.ufw', 'rb') as f:
    mod_ufw = f.read()

mod_app_area = bytearray(mod_ufw[0x400+0x2000 : 0x400+0x33CBA])
jl_sfc_cipher(mod_app_area, 0, len(mod_app_area), base=0, key=0x190D)
mod_app = mod_app_area[0x120 : 0x120 + len(orig_app)]

diffs = []
for i in range(len(orig_app)):
    if orig_app[i] != mod_app[i]:
        diffs.append(i)

print(f'Total byte differences in app.bin: {len(diffs)}')

ranges = []
for d in diffs:
    if not ranges or d > ranges[-1][1]:
        ranges.append([d, d+1])
    else:
        ranges[-1][1] = d + 1

for start, end in ranges:
    orig_chunk = orig_app[start:end]
    mod_chunk = mod_app[start:end]
    print(f'Diff at 0x{start:05X} (len {end-start}):')
    print(f'  Orig: {orig_chunk.hex(" ")} ({repr(orig_chunk)})')
    print(f'  Mod:  {mod_chunk.hex(" ")} ({repr(mod_chunk)})')
