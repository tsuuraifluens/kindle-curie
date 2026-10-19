import struct, hashlib, sys, os
sys.path.insert(0, os.path.abspath('scripts/jieli_tools'))
from jltech.cipher import jl_enc_cipher
from jltech.crc import jl_crc16

def parse_ufw_file(filepath):
    with open(filepath, 'rb') as f:
        raw = bytearray(f.read())

    print(f"================================================================================")
    print(f"UFW CONTAINER AUDIT REPORT: {filepath}")
    print(f"File Size: {len(raw)} bytes (0x{len(raw):X})")
    print(f"SHA-256:   {hashlib.sha256(raw).hexdigest().upper()}")
    print(f"================================================================================\n")

    # 1. Header (0x00 - 0x3F)
    hdr = bytearray(raw[:0x40])
    jl_enc_cipher(hdr, 0, 0x40, key=0xFFFF)

    hdrcrc, listcrc, imgsize, numents, wa3, wa4, chipname_raw = struct.unpack_from('<HHIHHI48s', hdr, 0)
    calc_hdrcrc = jl_crc16(hdr[2:0x40])
    chipname = chipname_raw.split(b'\x00')[0].decode('latin1', errors='replace')

    print(f"[1] UFW Container Header (0x40 Bytes, Obfuscation: Key 0xFFFF):")
    print(f"    - Header CRC16:      0x{hdrcrc:04X} (Calculated: 0x{calc_hdrcrc:04X}) -> {'MATCH' if hdrcrc == calc_hdrcrc else 'MISMATCH!'}")
    print(f"    - Entry List CRC16:  0x{listcrc:04X}")
    print(f"    - Image Total Size:  {imgsize} bytes (0x{imgsize:X}) -> Matches File Size: {'YES' if imgsize == len(raw) else 'NO'}")
    print(f"    - Number of Entries: {numents}")
    print(f"    - Alignment / Flags: wa3=0x{wa3:04X}, wa4=0x{wa4:08X} (512 bytes sector)")
    print(f"    - Target Chip Name:  '{chipname}'\n")

    # 2. Entry List (0x40 to 0x40 + numents * 0x50)
    headersize = 0x40 + numents * 0x50
    list_bytes = bytearray(raw[0x40 : headersize])
    calc_listcrc = jl_crc16(list_bytes)
    print(f"[2] UFW Entry Table (0x000040 - 0x{headersize:06X}, {numents} Entries, {numents * 0x50} Bytes):")
    print(f"    - Raw List CRC16:    0x{calc_listcrc:04X} (Header expects: 0x{listcrc:04X}) -> {'MATCH' if calc_listcrc == listcrc else 'MISMATCH!'}\n")

    entries = []
    print(f"    | {'Idx':<3} | {'Type':<6} | {'EIdx':<5} | {'CRC16':<6} | {'Offset':<10} | {'Size':<10} | {'Size2':<10} | {'Name':<16} |")
    print(f"    |{'-'*5}|{'-'*8}|{'-'*7}|{'-'*8}|{'-'*12}|{'-'*12}|{'-'*12}|{'-'*18}|")

    entry_blob = bytearray(raw[0x40 : headersize])
    for i in range(numents):
        ent_off = i * 0x50
        jl_enc_cipher(entry_blob, ent_off, 0x50, key=0xFFFF)
        etype, eindex, edcrc, ewa1, eoffset, esize, esize2, ewa2, ename = struct.unpack_from('<HHHHIII44s16s', entry_blob, ent_off)
        name = ename.split(b'\x00')[0].decode('latin1', errors='replace')
        entries.append({
            'idx': i,
            'type': etype,
            'eindex': eindex,
            'crc': edcrc,
            'offset': eoffset,
            'size': esize,
            'size2': esize2,
            'name': name
        })
        print(f"    | {i:<3} | 0x{etype:04X} | {eindex:<5} | 0x{edcrc:04X} | 0x{eoffset:08X} | 0x{esize:08X} | 0x{esize2:08X} | {name:<16} |")

    # 3. Analyze data regions and gaps
    print(f"\n[3] Container Data Segment Layout Analysis:")
    # Filter non-zero size entries
    active_ents = [e for e in entries if e['size'] > 0]
    sorted_ents = sorted(active_ents, key=lambda x: x['offset'])
    for idx, e in enumerate(sorted_ents):
        start = e['offset']
        end = start + e['size']
        chunk = raw[start:end]
        chunk_crc = jl_crc16(chunk)
        crc_match = 'MATCH' if chunk_crc == e['crc'] else f'MISMATCH (calc 0x{chunk_crc:04X})'
        print(f"    Entry {e['idx']} (Type 0x{e['type']:04X}, '{e['name']}'): File [0x{start:06X} - 0x{end:06X}] ({e['size']} bytes), CRC16: 0x{e['crc']:04X} -> {crc_match}")
        if idx < len(sorted_ents) - 1:
            next_start = sorted_ents[idx+1]['offset']
            if next_start > end:
                gap = next_start - end
                print(f"      -> GAP DETECTED: 0x{gap:X} ({gap}) bytes between 0x{end:06X} and 0x{next_start:06X}")
            elif next_start < end:
                overlap = end - next_start
                print(f"      -> OVERLAP DETECTED: 0x{overlap:X} ({overlap}) bytes!")

    last_end = sorted_ents[-1]['offset'] + sorted_ents[-1]['size']
    if last_end < len(raw):
        print(f"    Trailing Data: 0x{len(raw) - last_end:X} ({len(raw) - last_end}) bytes from 0x{last_end:06X} to EOF (0x{len(raw):06X})")

if __name__ == '__main__':
    parse_ufw_file('extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw')
