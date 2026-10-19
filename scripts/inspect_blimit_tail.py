import os, sys, struct
sys.path.insert(0, os.path.abspath('scripts/jieli_tools'))
from jltech.crc import jl_crc16, jl_crc32
from jltech.cipher import jl_enc_cipher

orig_ufw = open('extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw', 'rb').read()
mod_ufw = open('kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.0.ufw', 'rb').read()

# Check entries 1 to 7 byte-for-byte
for off in range(0x40, 0x40 + 8 * 0x50, 0x50):
    pass

orig_blimit = orig_ufw[0x09D760 : 0x09D760 + 144]
mod_blimit = mod_ufw[0x09D760 : 0x09D760 + 144]

print(f"blimit byte identical: {orig_blimit == mod_blimit}")
print(f"orig blimit hex: {orig_blimit[:32].hex(' ')}")
print(f"mod  blimit hex: {mod_blimit[:32].hex(' ')}")

# Decrypt blimit with key 0xFFFF or other?
dec_blimit = bytearray(orig_blimit)
jl_enc_cipher(dec_blimit, 0, len(dec_blimit), 0xFFFF)
print(f"dec blimit hex (key 0xFFFF): {dec_blimit[:32].hex(' ')}")
print(f"dec blimit crc: 0x{jl_crc16(dec_blimit):04X}")

# Check blimit with key 0x190D or check its header?
# Let's inspect the first 16 bytes of blimit
print("blimit unpacked:", struct.unpack_from('<HH', dec_blimit, 0))

orig_tail = orig_ufw[0x09D800 : 0x09D800 + 64]
mod_tail = mod_ufw[0x09D800 : 0x09D800 + 64]
print(f"tail byte identical: {orig_tail == mod_tail}")
print(f"tail CRC16: orig=0x{jl_crc16(orig_tail):04X}, mod=0x{jl_crc16(mod_tail):04X}")

