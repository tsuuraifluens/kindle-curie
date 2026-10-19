import os, sys, struct
sys.path.insert(0, os.path.abspath('scripts/jieli_tools'))
from jltech.cipher import jl_sfc_cipher

orig_ufw = open('extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw', 'rb').read()
mod_ufw = open('kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.0.ufw', 'rb').read()

chipkey = 0x190D
appbase = 0x2000
append = 0x033CBA
app_offset = 0x2120
app_size = 198824

def get_app_bin(ufw_bytes):
    flash = bytearray(ufw_bytes[0x400:0x400+212992])
    jl_sfc_cipher(flash, appbase, append - appbase, appbase, chipkey)
    return flash[app_offset : app_offset + app_size]

orig_app = get_app_bin(orig_ufw)
mod_app = get_app_bin(mod_ufw)

print("=== GATT ATTRIBUTE TABLE INSPECTION (0x22360 - 0x223E0) ===")
print("Original:")
for off in range(0x22370, 0x223E0, 16):
    chunk = orig_app[off:off+16]
    print(f"  0x{off:05X}: {chunk.hex(' ')}  |  {repr(chunk)}")

print("\nModified:")
for off in range(0x22370, 0x223E0, 16):
    chunk = mod_app[off:off+16]
    print(f"  0x{off:05X}: {chunk.hex(' ')}  |  {repr(chunk)}")

print("\nSpecific Check at 0x22380 - 0x22395 (Manufacturer Name):")
print("Orig [0x22380 : 0x22394]:", orig_app[0x22380 : 0x22394].hex(' '))
print("Mod  [0x22380 : 0x22394]:", mod_app[0x22380 : 0x22394].hex(' '))

print("\nSpecific Check at 0x223B8 - 0x223C8 (PnP ID):")
print("Orig [0x223B8 : 0x223C8]:", orig_app[0x223B8 : 0x223C8].hex(' '))
print("Mod  [0x223B8 : 0x223C8]:", mod_app[0x223B8 : 0x223C8].hex(' '))
