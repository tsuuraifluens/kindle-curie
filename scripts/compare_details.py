import struct, sys
sys.path.insert(0, 'scripts/jieli_tools')
from jltech.cipher import jl_enc_cipher
from jltech.crc import jl_crc16

orig_path = 'extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw'
mod_path = 'kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.0.ufw'

with open(orig_path, 'rb') as f:
    orig = bytearray(f.read())
with open(mod_path, 'rb') as f:
    mod = bytearray(f.read())

print(f"Orig size: {len(orig)}, Mod size: {len(mod)}")

# Decrypt headers
orig_hdr = bytearray(orig[:0x40])
mod_hdr = bytearray(mod[:0x40])
jl_enc_cipher(orig_hdr, 0, 0x40, key=0xFFFF)
jl_enc_cipher(mod_hdr, 0, 0x40, key=0xFFFF)

print("\n--- Header Comparison (Decrypted) ---")
print("Orig Hdr:", orig_hdr.hex(' '))
print("Mod  Hdr:", mod_hdr.hex(' '))

o_hdrcrc, o_listcrc, o_imgsz, o_num, o_w3, o_w4, o_chip = struct.unpack_from('<HHIHHI48s', orig_hdr, 0)
m_hdrcrc, m_listcrc, m_imgsz, m_num, m_w3, m_w4, m_chip = struct.unpack_from('<HHIHHI48s', mod_hdr, 0)

print(f"Orig: hdrcrc=0x{o_hdrcrc:04X}, listcrc=0x{o_listcrc:04X}, imgsz=0x{o_imgsz:X}, num={o_num}, w3=0x{o_w3:X}, w4=0x{o_w4:X}, chip={o_chip[:10]}")
print(f"Mod : hdrcrc=0x{m_hdrcrc:04X}, listcrc=0x{m_listcrc:04X}, imgsz=0x{m_imgsz:X}, num={m_num}, w3=0x{m_w3:X}, w4=0x{m_w4:X}, chip={m_chip[:10]}")

# Decrypt entries
headersize = 0x40 + 8 * 0x50
orig_ents = bytearray(orig[0x40:headersize])
mod_ents = bytearray(mod[0x40:headersize])

for i in range(8):
    off = i * 0x50
    jl_enc_cipher(orig_ents, off, 0x50, key=0xFFFF)
    jl_enc_cipher(mod_ents, off, 0x50, key=0xFFFF)
    
    o_t, o_idx, o_crc, o_wa1, o_off, o_sz, o_sz2, o_wa2, o_name = struct.unpack_from('<HHHHIII44s16s', orig_ents, off)
    m_t, m_idx, m_crc, m_wa1, m_off, m_sz, m_sz2, m_wa2, m_name = struct.unpack_from('<HHHHIII44s16s', mod_ents, off)
    
    name = o_name.split(b'\x00')[0].decode('latin1')
    print(f"\n--- Entry {i}: {name} ---")
    if orig_ents[off:off+0x50] == mod_ents[off:off+0x50]:
        print("  Entry table metadata IDENTICAL")
    else:
        print(f"  Orig: type=0x{o_t:04X}, crc=0x{o_crc:04X}, off=0x{o_off:06X}, sz=0x{o_sz:06X}, sz2=0x{o_sz2:06X}")
        print(f"  Mod : type=0x{m_t:04X}, crc=0x{m_crc:04X}, off=0x{m_off:06X}, sz=0x{m_sz:06X}, sz2=0x{m_sz2:06X}")
        # print differences
        for b in range(0x50):
            if orig_ents[off+b] != mod_ents[off+b]:
                print(f"    diff at +0x{b:02X}: orig=0x{orig_ents[off+b]:02X}, mod=0x{mod_ents[off+b]:02X}")

# Now check data payloads of all entries
print("\n--- Payload Differences ---")
for i in range(8):
    off = i * 0x50
    o_off = struct.unpack_from('<I', orig_ents, off + 8)[0]
    o_sz = struct.unpack_from('<I', orig_ents, off + 12)[0]
    name = orig_ents[off+0x40:off+0x50].split(b'\x00')[0].decode('latin1')
    
    o_data = orig[o_off : o_off + o_sz]
    m_data = mod[o_off : o_off + o_sz]
    
    if o_data == m_data:
        print(f"Entry {i} ({name}): Payload IDENTICAL ({len(o_data)} bytes)")
    else:
        diff_count = sum(1 for a, b in zip(o_data, m_data) if a != b)
        print(f"Entry {i} ({name}): Payload DIFFERS! {diff_count} bytes differ out of {len(o_data)} bytes")

# Check gaps between entries
print("\n--- Gap Differences ---")
gaps = [
    (0, 0x40), # header
    (0x40, headersize), # entry table
    (headersize, 0x400), # gap before flash.bin
    (0x34400 + 687, 0x346C0), # gap between isd_config and ota.bin
    (0x346C0 + 221048, 0x6A640), # gap between ota.bin and script.ver
    (0x6A640 + 29, 0x6A660), # gap between script.ver and flash2.bin
    (0x6A660 + 209152, 0x9D760), # gap between flash2.bin and blimit.bin
    (0x9D760 + 144, 0x9D800), # gap between blimit.bin and tail.bin
    (0x9D800 + 64, len(orig)), # trailing
]
for g_start, g_end in gaps:
    o_gap = orig[g_start:g_end]
    m_gap = mod[g_start:g_end]
    if o_gap != m_gap:
        print(f"GAP [0x{g_start:06X} - 0x{g_end:06X}] DIFFERS! ({len(o_gap)} bytes)")
    else:
        print(f"GAP [0x{g_start:06X} - 0x{g_end:06X}] identical")
