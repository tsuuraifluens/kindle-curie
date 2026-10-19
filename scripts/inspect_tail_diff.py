import sys
sys.path.insert(0, 'scripts/jieli_tools')
from jltech.cipher import jl_sfc_cipher

orig_path = 'extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw'
mod_path = 'kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.0.ufw'

with open(orig_path, 'rb') as f:
    orig = f.read()
with open(mod_path, 'rb') as f:
    mod = f.read()

orig_f = bytearray(orig[0x400 : 0x400 + 0x34000])
mod_f = bytearray(mod[0x400 : 0x400 + 0x34000])

print("Raw orig_f at 0x33CBA..0x33D00:")
print(orig_f[0x33CBA:0x33D00].hex(' '))
print("Raw mod_f at 0x33CBA..0x33D00:")
print(mod_f[0x33CBA:0x33D00].hex(' '))

# Let's decrypt both with SFC cipher
jl_sfc_cipher(orig_f, 0x2000, 0x34000 - 0x2000, base=0, key=0x190D)
jl_sfc_cipher(mod_f, 0x2000, 0x34000 - 0x2000, base=0, key=0x190D)

print("\nDecrypted orig_f at 0x33CBA..0x33D00:")
print(orig_f[0x33CBA:0x33D00].hex(' '))
print("Decrypted mod_f at 0x33CBA..0x33D00:")
print(mod_f[0x33CBA:0x33D00].hex(' '))

# Check what is after 0x33CBA in dec_bin
with open('extracted/ufw_unpacked/decrypted.bin', 'rb') as f:
    dec = f.read()
print("dec_bin at 0x33CBA..0x33D00:")
print(dec[0x33CBA:0x33D00].hex(' '))
