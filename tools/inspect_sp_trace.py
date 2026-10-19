with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    app = f.read()

base = 0x01E00120

# Let's inspect bytes at 0x04D50..0x04D90 in both orig and mod
v12_ufw = open('kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.2.ufw', 'rb').read()
import sys
sys.path.insert(0, 'kindle_click_jieli_port/tools/jieli_tools')
from jltech.cipher import jl_sfc_cipher
dec_flash = bytearray(v12_ufw[0x400:0x400+212992])
jl_sfc_cipher(dec_flash, 0x2000, 0x32000, 0x2000, key=0x190D)
mod_app = dec_flash[0x2120:0x2120+198824]

for off in range(0x04D50, 0x04DA0, 2):
    vaddr = base + off
    wo = app[off] | (app[off+1] << 8)
    wm = mod_app[off] | (mod_app[off+1] << 8)
    print(f'0x{off:04X} (V: 0x{vaddr:08X}): ORIG={wo:04X} ({app[off]:02x} {app[off+1]:02x}) | MOD={wm:04X} ({mod_app[off]:02x} {mod_app[off+1]:02x})')
