import struct, os, sys, hashlib

sys.path.insert(0, os.path.abspath('scripts/jieli_tools'))
from jltech.crc import jl_crc16
from jltech.cipher import jl_enc_cipher, jl_sfc_cipher

orig_ufw_path = r'extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw'
decrypted_bin_path = r'extracted/ufw_unpacked/decrypted.bin'

with open(orig_ufw_path, 'rb') as f:
    raw_ufw = bytearray(f.read())

with open(decrypted_bin_path, 'rb') as f:
    dec_bin = bytearray(f.read())

print(f"Original UFW size: {len(raw_ufw)} bytes")
print(f"Decrypted bin size: {len(dec_bin)} bytes")

# Base in decrypted.bin: app.bin is at 0x2120.
app_offset_in_dec = 0x2120
app_size = 198824

# 1. PnP ID at 0x223BC
pnp_off = app_offset_in_dec + 0x223BC
# 02 (USB-IF), 71 01 (VID 0x0171), 43 04 (PID 0x0443), 00 01 (Ver 0x0100)
dec_bin[pnp_off : pnp_off + 7] = bytes([0x02, 0x71, 0x01, 0x43, 0x04, 0x00, 0x01])

# 2. Manufacturer Name at 0x22384
manuf_off = app_offset_in_dec + 0x22384
dec_bin[manuf_off : manuf_off + 13] = b'Amazon\x00\x00\x00\x00\x00\x00\x00'

# 3. Reader Mode broadcast name at 0x211C6
rname_off = app_offset_in_dec + 0x211C6
dec_bin[rname_off : rname_off + 12] = b'Kindle-PT\x00\x00\x00'

# 4. Keyboard Mode broadcast name at 0x211EB
kbname_off = app_offset_in_dec + 0x211EB
dec_bin[kbname_off : kbname_off + 14] = b'Kindle Remote\x00'

# 5. Keyboard Mapping Table at 0x2289C
# Button A (0x2289C): PageDown 0x4E
btn_a_off = app_offset_in_dec + 0x2289C
dec_bin[btn_a_off : btn_a_off + 6] = bytes([0x02, 0x00, 0x00, 0x4E, 0x00, 0x00])

# Button B (0x228A2): PageUp 0x4B
btn_b_off = app_offset_in_dec + 0x228A2
dec_bin[btn_b_off : btn_b_off + 6] = bytes([0x02, 0x00, 0x00, 0x4B, 0x00, 0x00])

# Button X (0x228A8): Power 0x66
btn_x_off = app_offset_in_dec + 0x228A8
dec_bin[btn_x_off : btn_x_off + 6] = bytes([0x02, 0x00, 0x00, 0x66, 0x00, 0x00])

# Button Y (0x228AE): Power 0x66
btn_y_off = app_offset_in_dec + 0x228AE
dec_bin[btn_y_off : btn_y_off + 6] = bytes([0x02, 0x00, 0x00, 0x66, 0x00, 0x00])

# D-pad Up (0x228FC): PageUp 0x4B
dpad_up_off = app_offset_in_dec + 0x228FC
dec_bin[dpad_up_off : dpad_up_off + 6] = bytes([0x02, 0x00, 0x00, 0x4B, 0x00, 0x00])

# D-pad Down (0x22902): PageDown 0x4E
dpad_down_off = app_offset_in_dec + 0x22902
dec_bin[dpad_down_off : dpad_down_off + 6] = bytes([0x02, 0x00, 0x00, 0x4E, 0x00, 0x00])

# D-pad Left (0x22908): PageUp 0x4B
dpad_left_off = app_offset_in_dec + 0x22908
dec_bin[dpad_left_off : dpad_left_off + 6] = bytes([0x02, 0x00, 0x00, 0x4B, 0x00, 0x00])

# D-pad Right (0x2290E): PageDown 0x4E
dpad_right_off = app_offset_in_dec + 0x2290E
dec_bin[dpad_right_off : dpad_right_off + 6] = bytes([0x02, 0x00, 0x00, 0x4E, 0x00, 0x00])

# 6. Recompute JLFS CRC for app.bin
patched_app = dec_bin[app_offset_in_dec : app_offset_in_dec + app_size]
new_app_edcrc = jl_crc16(patched_app)
print(f"Patched app.bin CRC16: 0x{new_app_edcrc:04X}")

# Update Entry header at 0x2020:
entry_hdr = bytearray(dec_bin[0x2022 : 0x2040])
struct.pack_into('<H', entry_hdr, 0, new_app_edcrc)
new_entry_hcrc = jl_crc16(entry_hdr)
struct.pack_into('<H', dec_bin, 0x2020, new_entry_hcrc)
dec_bin[0x2022 : 0x2040] = entry_hdr
print(f"Patched JLFS entry hcrc: 0x{new_entry_hcrc:04X}")

# 7. Re-encrypt app area using jl_sfc_cipher with ChipKey 0x190D
chipkey = 0x190D
appbase = 0x2000
recrypted_app_area = bytearray(dec_bin[appbase : 0x34000])
jl_sfc_cipher(recrypted_app_area, 0, len(recrypted_app_area), base=0, key=chipkey)

# 8. Update raw_ufw flash.bin portion
flash_base = 0x400
raw_ufw[flash_base + appbase : flash_base + 0x34000] = recrypted_app_area

# 9. Recompute flash.bin edcrc in Entry 0
flash_bin_data = raw_ufw[flash_base : flash_base + 212992]
new_flash_crc = jl_crc16(flash_bin_data)
print(f"Patched flash.bin CRC16: 0x{new_flash_crc:04X}")

entry0 = bytearray(raw_ufw[0x40 : 0x90])
jl_enc_cipher(entry0, 0, len(entry0), key=0xFFFF)
struct.pack_into('<H', entry0, 4, new_flash_crc)
jl_enc_cipher(entry0, 0, len(entry0), key=0xFFFF)
raw_ufw[0x40 : 0x90] = entry0

# 10. Update UFW listcrc and hdrcrc
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
print(f"New UFW Header CRC16: 0x{new_hdrcrc:04X}, List CRC16: 0x{new_listcrc:04X}")

out_path = r'candidate/test_v1.0.ufw'
with open(out_path, 'wb') as f:
    f.write(raw_ufw)
print(f"Saved test firmware: {out_path} ({len(raw_ufw)} bytes)")
print(f"SHA-256: {hashlib.sha256(raw_ufw).hexdigest().upper()}")
