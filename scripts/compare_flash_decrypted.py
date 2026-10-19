import struct, sys
sys.path.insert(0, 'scripts/jieli_tools')
from jltech.cipher import jl_enc_cipher
from jltech.crc import jl_crc16

orig_path = 'extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw'
mod_path = 'kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.0.ufw'

with open(orig_path, 'rb') as f:
    orig_ufw = f.read()
with open(mod_path, 'rb') as f:
    mod_ufw = f.read()

# Entry 0 is at 0x400, size 0x34000
orig_f = bytearray(orig_ufw[0x400 : 0x400 + 0x34000])
mod_f = bytearray(mod_ufw[0x400 : 0x400 + 0x34000])

print(f"Orig flash size: {len(orig_f)}, Mod flash size: {len(mod_f)}")

# In flash.bin, how is it structured?
# First 32 bytes is flash header (encrypted with 0xFFFF)
orig_hdr = bytearray(orig_f[:32])
mod_hdr = bytearray(mod_f[:32])
jl_enc_cipher(orig_hdr, 0, 32, key=0xFFFF)
jl_enc_cipher(mod_hdr, 0, 32, key=0xFFFF)

print("Decrypted flash header:")
print("Orig:", orig_hdr.hex(' '))
print("Mod :", mod_hdr.hex(' '))

# Let's inspect differences in the raw flash.bin
diffs = [i for i in range(len(orig_f)) if orig_f[i] != mod_f[i]]
print(f"Total byte differences in flash.bin: {len(diffs)}")

# Group diffs into contiguous ranges
ranges = []
for d in diffs:
    if not ranges or d > ranges[-1][1] + 1:
        ranges.append([d, d])
    else:
        ranges[-1][1] = d

print(f"Diff ranges ({len(ranges)} ranges):")
for r_start, r_end in ranges:
    sz = r_end - r_start + 1
    print(f"  0x{r_start:06X} - 0x{r_end:06X} (size {sz})")
