import struct, sys
sys.path.insert(0, 'scripts/jieli_tools')
from jltech.cipher import jl_enc_cipher
from jltech.crc import jl_crc16

orig_path = 'extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw'
update_path = 'extracted/L1161兼容阅星瞳/update.ufw'

with open(orig_path, 'rb') as f: orig = bytearray(f.read())
with open(update_path, 'rb') as f: upd = bytearray(f.read())

print(f"Orig size: {len(orig)}, Update size: {len(upd)}")

# Decrypt headers
orig_hdr = bytearray(orig[:0x40])
upd_hdr = bytearray(upd[:0x40])
jl_enc_cipher(orig_hdr, 0, 0x40, key=0xFFFF)
jl_enc_cipher(upd_hdr, 0, 0x40, key=0xFFFF)

print("Orig Hdr:", orig_hdr.hex(' '))
print("Upd  Hdr:", upd_hdr.hex(' '))

# Compare entries
headersize = 0x40 + 8 * 0x50
orig_ents = bytearray(orig[0x40:headersize])
upd_ents = bytearray(upd[0x40:headersize])

for i in range(8):
    off = i * 0x50
    jl_enc_cipher(orig_ents, off, 0x50, key=0xFFFF)
    jl_enc_cipher(upd_ents, off, 0x50, key=0xFFFF)
    o_name = orig_ents[off+0x40:off+0x50].split(b'\x00')[0].decode('latin1')
    u_name = upd_ents[off+0x40:off+0x50].split(b'\x00')[0].decode('latin1')
    if orig_ents[off:off+0x50] == upd_ents[off:off+0x50]:
        print(f"Entry {i} ({o_name}): Metadata IDENTICAL")
    else:
        print(f"Entry {i} ({o_name}): Metadata DIFFERS!")
    
    o_off = struct.unpack_from('<I', orig_ents, off + 8)[0]
    o_sz = struct.unpack_from('<I', orig_ents, off + 12)[0]
    u_off = struct.unpack_from('<I', upd_ents, off + 8)[0]
    u_sz = struct.unpack_from('<I', upd_ents, off + 12)[0]
    
    o_data = orig[o_off : o_off + o_sz]
    u_data = upd[u_off : u_off + u_sz]
    if o_data == u_data:
        print(f"  Payload IDENTICAL ({len(o_data)} bytes)")
    else:
        diff_cnt = sum(1 for a, b in zip(o_data, u_data) if a != b)
        print(f"  Payload DIFFERS! {diff_cnt} bytes differ")
