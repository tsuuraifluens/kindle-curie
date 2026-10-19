import os, sys, struct, hashlib
sys.path.insert(0, os.path.abspath('scripts/jieli_tools'))
from jltech.crc import jl_crc16, jl_crc32
from jltech.cipher import jl_enc_cipher, jl_sfc_cipher
from jltech.chipkeybin import chipkeybin_decode
from jltech.utils import nulltermstr

orig_ufw = open('extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw', 'rb').read()
mod_ufw = open('kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.0.ufw', 'rb').read()

chipkey = 0x190D
appbase = 0x2000
append = 0x033CBA

# 1. Unpack flash.bin
orig_flash = bytearray(orig_ufw[0x400:0x400+212992])
mod_flash = bytearray(mod_ufw[0x400:0x400+212992])

# Decrypt app area
orig_sfc = bytearray(orig_flash)
jl_sfc_cipher(orig_sfc, appbase, append - appbase, appbase, chipkey)

mod_sfc = bytearray(mod_flash)
jl_sfc_cipher(mod_sfc, appbase, append - appbase, appbase, chipkey)

# Extract app.bin (offset 0x2120 in flash, size 198824 = 0x308A8)
app_offset = 0x2120
app_size = 198824

orig_app = orig_sfc[app_offset : app_offset + app_size]
mod_app = mod_sfc[app_offset : app_offset + app_size]

print(f"app.bin size: {len(orig_app)} bytes")
print(f"Original app.bin CRC16: 0x{jl_crc16(orig_app):04X}, SHA256: {hashlib.sha256(orig_app).hexdigest()}")
print(f"Modified app.bin CRC16: 0x{jl_crc16(mod_app):04X}, SHA256: {hashlib.sha256(mod_app).hexdigest()}")

# Compare app.bin byte-by-byte
app_diffs = []
for i in range(len(orig_app)):
    if orig_app[i] != mod_app[i]:
        app_diffs.append((i, orig_app[i], mod_app[i]))

print(f"\nTotal modified bytes in app.bin: {len(app_diffs)}")

# Check text segment [0x00000 - 0x20000]
text_diffs = [d for d in app_diffs if d[0] < 0x20000]
print(f"Modified bytes in text segment (0x00000 - 0x20000): {len(text_diffs)}")

# Group diffs into contiguous chunks
chunks = []
if app_diffs:
    cur_chunk = [app_diffs[0]]
    for d in app_diffs[1:]:
        if d[0] == cur_chunk[-1][0] + 1:
            cur_chunk.append(d)
        else:
            chunks.append(cur_chunk)
            cur_chunk = [d]
    chunks.append(cur_chunk)

print(f"Total patch blocks/chunks: {len(chunks)}")
for c in chunks:
    start_off = c[0][0]
    end_off = c[-1][0]
    length = len(c)
    orig_b = bytes([x[1] for x in c])
    mod_b = bytes([x[2] for x in c])
    flash_off = app_offset + start_off
    cpu_addr = 0x01E00120 + start_off
    print(f"Chunk @ app.bin 0x{start_off:05X} (flash 0x{flash_off:05X}, CPU 0x{cpu_addr:08X}), len={length}:")
    print(f"  Orig: {orig_b.hex(' ')}  |  {repr(orig_b)}")
    print(f"  Mod:  {mod_b.hex(' ')}  |  {repr(mod_b)}")
