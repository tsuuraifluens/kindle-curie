import struct, sys
sys.path.insert(0, 'scripts/jieli_tools')
from jltech.cipher import jl_enc_cipher
from jltech.crc import jl_crc16

def inspect_entries(filepath):
    print("==================================================")
    print("Inspecting entries of:", filepath)
    with open(filepath, 'rb') as f:
        data = f.read()

    headersize = 0x40 + 8 * 0x50
    entry_blob = bytearray(data[0x40:headersize])
    for i in range(8):
        off = i * 0x50
        jl_enc_cipher(entry_blob, off, 0x50, key=0xFFFF)
        etype, eidx, edcrc, ewa1, eoffset, esize, esize2, ewa2, ename = struct.unpack_from('<HHHHIII44s16s', entry_blob, off)
        name = ename.split(b'\x00')[0].decode('latin1')
        chunk = data[eoffset : eoffset + esize]
        print(f"\n--- Entry {i}: '{name}' (Type 0x{etype:04X}, Offset 0x{eoffset:X}, Size {esize} / 0x{esize:X}) ---")
        print(f"Header CRC: 0x{edcrc:04X}, wa1: 0x{ewa1:04X}, wa2: {ewa2[:16].hex(' ')}")
        print("First 48 bytes:", chunk[:48].hex(' '))
        # Check if chunk contains ascii strings
        import re
        strs = re.findall(b'[\x20-\x7e]{3,}', chunk[:256])
        if strs:
            print("Ascii strings in first 256B:", [s.decode('latin1') for s in strs[:10]])

inspect_entries('extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw')
