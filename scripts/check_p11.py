import struct, sys
sys.path.insert(0, 'scripts/jieli_tools')
from jltech.crc import jl_crc16
from jltech.cipher import jl_sfc_cipher

orig_path = 'extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw'
mod_path = 'kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.0.ufw'

with open(orig_path, 'rb') as f: orig_ufw = f.read()
with open(mod_path, 'rb') as f: mod_ufw = f.read()

orig_f = bytearray(orig_ufw[0x400 : 0x400 + 0x34000])
mod_f = bytearray(mod_ufw[0x400 : 0x400 + 0x34000])

jl_sfc_cipher(orig_f, 0x2000, 0x34000 - 0x2000, base=0x2000, key=0x190D)
jl_sfc_cipher(mod_f, 0x2000, 0x34000 - 0x2000, base=0x2000, key=0x190D)

p11_off = 0x032C9A
p11_hdr_orig = orig_f[p11_off : p11_off + 32]
p11_hdr_mod  = mod_f[p11_off : p11_off + 32]

print("p11 hdr orig:", p11_hdr_orig.hex(' '))
print("p11 hdr mod :", p11_hdr_mod.hex(' '))

p11_data_orig = orig_f[p11_off + 32 : p11_off + 32 + 0x1000]
p11_data_mod  = mod_f[p11_off + 32 : p11_off + 32 + 0x1000]

print("p11 data match:", p11_data_orig == p11_data_mod)
print(f"CRC orig: 0x{jl_crc16(p11_data_orig):04X}")
print(f"CRC mod : 0x{jl_crc16(p11_data_mod):04X}")

# What offset does p11_code.bin say its data is at?
edcrc, eoff, esize, eflags, eresvd, eindex, ename = struct.unpack('<HIIBBH16s', p11_hdr_orig[2:])
print(f"eoff=0x{eoff:X}, esize=0x{esize:X}")
print(f"CRC of orig_f[eoff : eoff+esize]: 0x{jl_crc16(orig_f[eoff : eoff+esize]):04X}")
print(f"CRC of orig_f[0x2000+eoff : 0x2000+eoff+esize]: 0x{jl_crc16(orig_f[0x2000+eoff : 0x2000+eoff+esize]):04X}")
