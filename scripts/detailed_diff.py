import struct, sys
sys.path.insert(0, 'scripts/jieli_tools')
from jltech.cipher import jl_sfc_cipher
from jltech.crc import jl_crc16

orig_path = 'extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw'
mod_path = 'kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.0.ufw'

with open(orig_path, 'rb') as f: orig_ufw = f.read()
with open(mod_path, 'rb') as f: mod_ufw = f.read()

orig_f = bytearray(orig_ufw[0x400 : 0x400 + 0x34000])
mod_f = bytearray(mod_ufw[0x400 : 0x400 + 0x34000])

jl_sfc_cipher(orig_f, 0x2000, 0x34000 - 0x2000, base=0x2000, key=0x190D)
jl_sfc_cipher(mod_f, 0x2000, 0x34000 - 0x2000, base=0x2000, key=0x190D)

diffs = [i for i in range(len(orig_f)) if orig_f[i] != mod_f[i]]
print(f"Total differences: {len(diffs)} bytes")

# Group into contiguous ranges
ranges = []
for d in diffs:
    if not ranges or d > ranges[-1][1] + 1:
        ranges.append([d, d])
    else:
        ranges[-1][1] = d

for r_start, r_end in ranges:
    sz = r_end - r_start + 1
    # Check what section this is in
    sec = "unknown"
    if r_start < 0x20:
        sec = "flash header"
    elif r_start < 0x2000:
        sec = "top entries / uboot"
    elif 0x2000 <= r_start < 0x2020:
        sec = "app_area_head header"
    elif 0x2020 <= r_start < 0x2120:
        sec = "app_dir_head subentries"
    elif 0x2120 <= r_start < 0x2120 + 198824:
        app_off = r_start - 0x2120
        sec = f"app.bin (+0x{app_off:X})"
    elif 0x32C9A <= r_start < 0x33CBA:
        sec = "p11_code.bin"
    elif r_start >= 0x33CBA:
        sec = f"trailing padding (+0x{r_start - 0x33CBA:X})"
    print(f"  [0x{r_start:06X} - 0x{r_end:06X}] ({sz:3d} B): {sec}")
