import os
import sys
import struct
import hashlib

sys.path.insert(0, os.path.abspath('scripts/jieli_tools'))
from jltech.crc import jl_crc16, jl_crc32
from jltech.cipher import jl_enc_cipher, jl_sfc_cipher
from jltech.chipkeybin import chipkeybin_decode
from jltech.utils import nulltermstr

orig_ufw_path = 'extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw'
mod_ufw_path = 'kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.0.ufw'

with open(orig_ufw_path, 'rb') as f:
    orig_ufw = bytearray(f.read())
with open(mod_ufw_path, 'rb') as f:
    mod_ufw = bytearray(f.read())

print("================================================================================")
print("FIRMWARE CONTAINER PARSING & INTEGRITY CHECK")
print("================================================================================")

def parse_ufw(data, label):
    hdr = bytearray(data[:0x40])
    jl_enc_cipher(hdr, 0, 0x40, key=0xFFFF)
    hdrcrc, listcrc, imgsize, numents, wa3, wa4, chipname = struct.unpack_from('<HHIHHI48s', hdr, 0)
    calc_hdrcrc = jl_crc16(hdr[2:0x40])
    headersize = 0x40 + numents * 0x50
    calc_listcrc = jl_crc16(data[0x40:headersize])
    
    print(f"\n--- {label} ---")
    print(f"File size: {len(data)} (Header imgsize: {imgsize})")
    print(f"Header CRC: 0x{hdrcrc:04X} (calc: 0x{calc_hdrcrc:04X}) -> {'MATCH' if hdrcrc == calc_hdrcrc else 'MISMATCH'}")
    print(f"List CRC:   0x{listcrc:04X} (calc: 0x{calc_listcrc:04X}) -> {'MATCH' if listcrc == calc_listcrc else 'MISMATCH'}")
    print(f"Entries count: {numents}, Chip: {chipname.split(b'\x00')[0].decode('ascii')}")

    entry_blob = bytearray(data[0x40:headersize])
    entries = []
    for i in range(numents):
        off = i * 0x50
        jl_enc_cipher(entry_blob, off, 0x50, key=0xFFFF)
        etype, eidx, edcrc, ewa1, eoffset, esize, esize2, ewa2, ename = struct.unpack_from('<HHHHIII44s16s', entry_blob, off)
        name_str = ename.split(b"\x00")[0].decode('latin1', errors='replace')
        chunk = data[eoffset : eoffset + esize]
        calc_crc = jl_crc16(chunk) if esize > 0 else 0
        entries.append({
            'idx': i, 'type': etype, 'eidx': eidx, 'crc': edcrc, 'calc_crc': calc_crc,
            'offset': eoffset, 'size': esize, 'size2': esize2, 'name': name_str,
            'match': (calc_crc == edcrc)
        })
        print(f"  [{i}] {name_str:<16} Type=0x{etype:04X} Idx={eidx} Off=0x{eoffset:06X} Size={esize:6d} CRC=0x{edcrc:04X} (calc=0x{calc_crc:04X}) -> {'MATCH' if calc_crc == edcrc else 'MISMATCH'}")
    return entries

orig_entries = parse_ufw(orig_ufw, "ORIGINAL UFW")
mod_entries = parse_ufw(mod_ufw, "MODIFIED UFW")

