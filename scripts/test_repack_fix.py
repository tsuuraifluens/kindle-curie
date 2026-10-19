import os
import sys
import struct
import hashlib

sys.path.insert(0, 'scripts/jieli_tools')
from jltech.crc import jl_crc16
from jltech.cipher import jl_enc_cipher, jl_sfc_cipher
from jltech.utils import nulltermstr

orig_ufw_path = 'extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw'
decrypted_bin_path = 'extracted/ufw_unpacked/decrypted.bin'
candidate_ufw_path = 'kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.0_candidate.ufw'

with open(orig_ufw_path, 'rb') as f:
    raw_ufw = bytearray(f.read())
with open(decrypted_bin_path, 'rb') as f:
    dec_bin = bytearray(f.read())

app_offset_in_dec = 0x2120
app_size = 198824

# Patches from build_kindle_click_firmware.py
patches = [
    ("GAP Name (Reader Mode)", 0x211C6, b'Kindle-PT\x00\x00\x00'),
    ("GAP Name (Keyboard Mode)", 0x211EB, b'Kindle Remote\x00'),
    ("Amazon Lab126 VID/PID EIR Constant (0x211F9)", 0x211F9, bytes([0x71, 0x01, 0x04, 0x43])),
    ("DIS Manufacturer Name", 0x22384, b'Amazon\x00\x00\x00\x00\x00\x00\x00'),
    ("DIS PnP ID (Amazon Lab126 VID:0x0171 PID:0x0443)", 0x223BC, bytes([0x02, 0x71, 0x01, 0x43, 0x04, 0x00, 0x01])),
    ("BLE ADV AD 0xFF Injection (adv_setup_init)", 0x04DC2, bytes([
        0x50, 0x70,
        0x78, 0x68,
        0xF0, 0x7F,
        0x88, 0x68,
        0x90, 0x98,
        0xC1, 0xFF, 0x19, 0x13, 0xE2, 0x01,
        0x42, 0x70,
        0x98, 0xF3, 0xF6, 0x0F
    ])),
    ("BLE ADV Name Offset Adjust (adv_setup_init)", 0x04DFA, bytes([0xD5, 0x9E])),
    ("Reader Mode Dispatch -> IINE_keyboard", 0x0712E, bytes([0xC6, 0xFF, 0x0B, 0x13, 0xE2, 0x01])),
    ("Table 1 Button A -> KEY_PAGEDOWN (0x4E)", 0x2289C, bytes([0x02, 0x00, 0x00, 0x4E, 0x00, 0x00])),
    ("Table 1 Button B -> KEY_PAGEUP (0x4B)", 0x228A2, bytes([0x02, 0x00, 0x00, 0x4B, 0x00, 0x00])),
    ("Table 1 Button X -> KEY_POWER (0x66)", 0x228A8, bytes([0x02, 0x00, 0x00, 0x66, 0x00, 0x00])),
    ("Table 1 Button Y -> KEY_POWER (0x66)", 0x228AE, bytes([0x02, 0x00, 0x00, 0x66, 0x00, 0x00])),
    ("Table 1 D-pad Up -> KEY_PAGEUP (0x4B)", 0x228FC, bytes([0x02, 0x00, 0x00, 0x4B, 0x00, 0x00])),
    ("Table 1 D-pad Down -> KEY_PAGEDOWN (0x4E)", 0x22902, bytes([0x02, 0x00, 0x00, 0x4E, 0x00, 0x00])),
    ("Table 1 D-pad Left -> KEY_BRIGHTNESSDOWN (0x70)", 0x22908, bytes([0x02, 0x00, 0x00, 0x70, 0x00, 0x00])),
    ("Table 1 D-pad Right -> KEY_BRIGHTNESSUP (0x6F)", 0x2290E, bytes([0x02, 0x00, 0x00, 0x6F, 0x00, 0x00])),
    ("Table 3 Button A -> KEY_PAGEDOWN (0x4E)", 0x22A1C, bytes([0x02, 0x00, 0x00, 0x4E, 0x00, 0x00])),
    ("Table 3 Button B -> KEY_PAGEUP (0x4B)", 0x22A22, bytes([0x02, 0x00, 0x00, 0x4B, 0x00, 0x00])),
    ("Table 3 Button X -> KEY_POWER (0x66)", 0x22A28, bytes([0x02, 0x00, 0x00, 0x66, 0x00, 0x00])),
    ("Table 3 Button Y -> KEY_POWER (0x66)", 0x22A2E, bytes([0x02, 0x00, 0x00, 0x66, 0x00, 0x00])),
    ("Table 3 D-pad Up -> KEY_PAGEUP (0x4B)", 0x22A7C, bytes([0x02, 0x00, 0x00, 0x4B, 0x00, 0x00])),
    ("Table 3 D-pad Down (Vol+) -> KEY_PAGEDOWN (0x4E)", 0x22A82, bytes([0x02, 0x00, 0x00, 0x4E, 0x00, 0x00])),
    ("Table 3 D-pad Left (Vol-) -> KEY_BRIGHTNESSDOWN (0x70)", 0x22A88, bytes([0x02, 0x00, 0x00, 0x70, 0x00, 0x00])),
    ("Table 3 D-pad Right (Prev) -> KEY_BRIGHTNESSUP (0x6F)", 0x22A8E, bytes([0x02, 0x00, 0x00, 0x6F, 0x00, 0x00])),
    ("Table 3 Next Track -> KEY_BRIGHTNESSUP (0x6F)", 0x22A94, bytes([0x02, 0x00, 0x00, 0x6F, 0x00, 0x00])),
]

for name, off, pbytes in patches:
    target_off = app_offset_in_dec + off
    dec_bin[target_off : target_off + len(pbytes)] = pbytes

# Level 1: Update app.bin entry CRC at 0x2020
patched_app = dec_bin[app_offset_in_dec : app_offset_in_dec + app_size]
new_app_edcrc = jl_crc16(patched_app)
print(f"app.bin payload CRC16: 0x{new_app_edcrc:04X}")

entry_hdr = bytearray(dec_bin[0x2022 : 0x2040])
struct.pack_into('<H', entry_hdr, 0, new_app_edcrc)
new_entry_hcrc = jl_crc16(entry_hdr)
struct.pack_into('<H', dec_bin, 0x2020, new_entry_hcrc)
dec_bin[0x2022 : 0x2040] = entry_hdr
print(f"app.bin entry header CRC16: 0x{new_entry_hcrc:04X}")

# Level 2: Update app_area_head entry CRC at 0x2000
# app_area_head data is dec_bin[0x2020 : 0x32C9A] (length 0x30C7A)
area_data = dec_bin[0x2020 : 0x2020 + 0x30C7A]
new_area_edcrc = jl_crc16(area_data)
print(f"app_area_head data CRC16: 0x{new_area_edcrc:04X}")

area_hdr = bytearray(dec_bin[0x2002 : 0x2020])
struct.pack_into('<H', area_hdr, 0, new_area_edcrc)
new_area_hcrc = jl_crc16(area_hdr)
struct.pack_into('<H', dec_bin, 0x2000, new_area_hcrc)
dec_bin[0x2002 : 0x2020] = area_hdr
print(f"app_area_head header CRC16: 0x{new_area_hcrc:04X}")

# Level 3: Re-encrypt app area with SFC cipher up to 0x33CBA (leaving 0x33CBA..0x34000 as 0xFF)
chipkey = 0x190D
appbase = 0x2000
append = 0x033CBA
recrypted_app_area = bytearray(dec_bin[appbase : append])
jl_sfc_cipher(recrypted_app_area, 0, len(recrypted_app_area), base=0, key=chipkey)

flash_base = 0x400
raw_ufw[flash_base + appbase : flash_base + append] = recrypted_app_area
# Ensure trailing bytes in flash.bin are 0xFF
raw_ufw[flash_base + append : flash_base + 0x34000] = b'\xFF' * (0x34000 - append)

# Level 4: Recalculate flash.bin CRC in Entry 0
flash_bin_data = raw_ufw[flash_base : flash_base + 0x34000]
new_flash_crc = jl_crc16(flash_bin_data)
print(f"flash.bin full image CRC16: 0x{new_flash_crc:04X}")

entry0 = bytearray(raw_ufw[0x40 : 0x90])
jl_enc_cipher(entry0, 0, len(entry0), key=0xFFFF)
struct.pack_into('<H', entry0, 4, new_flash_crc)
jl_enc_cipher(entry0, 0, len(entry0), key=0xFFFF)
raw_ufw[0x40 : 0x90] = entry0

# Level 5 & 6: Recalculate Entry List CRC and Header CRC
ufw_hdr = bytearray(raw_ufw[:0x40])
jl_enc_cipher(ufw_hdr, 0, 0x40, key=0xFFFF)
hdrcrc, listcrc, imgsize, numents, wa3, wa4, chipname = struct.unpack_from('<HHIHHI48s', ufw_hdr, 0)
headersize = 0x40 + numents * 0x50

new_listcrc = jl_crc16(raw_ufw[0x40 : headersize])
struct.pack_into('<H', ufw_hdr, 2, new_listcrc)

new_hdrcrc = jl_crc16(ufw_hdr[2 : 0x40])
struct.pack_into('<H', ufw_hdr, 0, new_hdrcrc)

jl_enc_cipher(ufw_hdr, 0, 0x40, key=0xFFFF)
raw_ufw[:0x40] = ufw_hdr

with open(candidate_ufw_path, 'wb') as f:
    f.write(raw_ufw)

print(f"Candidate written to: {candidate_ufw_path}")
