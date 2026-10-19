import struct, os, sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'jieli_tools'))
from jltech.crc import jl_crc16
from jltech.cipher import jl_enc_cipher, jl_sfc_cipher

orig_ufw_path = r'extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw'
decrypted_bin_path = r'extracted/ufw_unpacked/decrypted.bin'
candidate_dir = r'candidate'
os.makedirs(candidate_dir, exist_ok=True)

with open(orig_ufw_path, 'rb') as f:
    raw_ufw = bytearray(f.read())

with open(decrypted_bin_path, 'rb') as f:
    dec_bin = bytearray(f.read())

print(f'Original UFW length: {len(raw_ufw)}')

# 1. Apply Candidate Patches to app.bin inside dec_bin:
# Base in decrypted.bin: app.bin is at 0x2120.
pnp_off = 0x2120 + 0x223BC
dec_bin[pnp_off+4 : pnp_off+10] = bytes([0x71, 0x01, 0x43, 0x04, 0x00, 0x01])

manuf_off = 0x2120 + 0x22384
dec_bin[manuf_off:manuf_off+13] = b'Amazon\x00\x00\x00\x00\x00\x00\x00'

name_off = 0x2120 + 0x211C6
dec_bin[name_off:name_off+12] = b'Kindle-PT\x00\x00\x00'

# 2. Update app.bin JLFS Entry at 0x2020 in dec_bin:
app_size = 198824
patched_app_data = dec_bin[0x2120 : 0x2120 + app_size]
new_app_edcrc = jl_crc16(patched_app_data)
print(f'New app.bin edcrc: 0x{new_app_edcrc:04X}')

# Entry at 0x2020: [2 bytes hcrc][30 bytes hdr]
entry_hdr = bytearray(dec_bin[0x2022:0x2040])
struct.pack_into('<H', entry_hdr, 0, new_app_edcrc) # edcrc is at offset 0 of hdr
new_entry_hcrc = jl_crc16(entry_hdr)
struct.pack_into('<H', dec_bin, 0x2020, new_entry_hcrc)
dec_bin[0x2022:0x2040] = entry_hdr
print(f'New app.bin entry hcrc: 0x{new_entry_hcrc:04X}')

# 3. Re-encrypt app area (0x2000 to 0x34000) using jl_sfc_cipher
chipkey = 0x190D
appbase = 0x2000
recrypted_app_area = bytearray(dec_bin[appbase:0x34000])
jl_sfc_cipher(recrypted_app_area, 0, len(recrypted_app_area), base=0, key=chipkey)

# 4. In raw_ufw, replace ONLY the app area at offset 0x400 + 0x2000 to 0x400 + 0x34000
flash_base = 0x400
raw_ufw[flash_base + appbase : flash_base + 0x34000] = recrypted_app_area

# 5. Recompute flash.bin edcrc:
flash_bin_data = raw_ufw[flash_base : flash_base + 212992]
new_flash_crc = jl_crc16(flash_bin_data)
print(f'New flash.bin CRC16: 0x{new_flash_crc:04X}')

# Update Entry 0 in UFW:
entry0 = bytearray(raw_ufw[0x40:0x90])
jl_enc_cipher(entry0, 0, len(entry0), key=0xFFFF)
struct.pack_into('<H', entry0, 4, new_flash_crc)
jl_enc_cipher(entry0, 0, len(entry0), key=0xFFFF)
raw_ufw[0x40:0x90] = entry0

# 6. Read numents from header and update listcrc across ALL entries (0x40 to 0x40 + numents * 0x50):
ufw_hdr = bytearray(raw_ufw[:0x40])
jl_enc_cipher(ufw_hdr, 0, 0x40, key=0xFFFF)
hdrcrc, listcrc, imgsize, numents, wa3, wa4, chipname = struct.unpack_from('<HHIHHI48s', ufw_hdr, 0)
headersize = 0x40 + numents * 0x50
print(f'UFW numents={numents}, headersize=0x{headersize:X}')

# Calculate listcrc over all entries (0x40 to headersize)
new_listcrc = jl_crc16(raw_ufw[0x40:headersize])
struct.pack_into('<H', ufw_hdr, 2, new_listcrc)

# Recompute hdrcrc over remaining 62 bytes (offset 2 to 0x40)
new_hdrcrc = jl_crc16(ufw_hdr[2:0x40])
struct.pack_into('<H', ufw_hdr, 0, new_hdrcrc)

# Re-encrypt header with 0xFFFF
jl_enc_cipher(ufw_hdr, 0, 0x40, key=0xFFFF)
raw_ufw[:0x40] = ufw_hdr
print(f'New UFW Header CRC16: 0x{new_hdrcrc:04X}, List CRC16: 0x{new_listcrc:04X}')

out_cand_path = os.path.join(candidate_dir, 'IINE_Kindle_Mod_Candidate_v0.1.ufw')
with open(out_cand_path, 'wb') as f:
    f.write(raw_ufw)
print(f'Saved candidate: {out_cand_path}')
