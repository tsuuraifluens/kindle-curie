import struct, sys
sys.path.insert(0, 'scripts/jieli_tools')
from jltech.cipher import jl_enc_cipher
from jltech.crc import jl_crc16

def simulate_parse(filepath):
    print("==================================================")
    print("Simulating jl_format_ufw::parse for:", filepath)
    with open(filepath, 'rb') as f:
        raw = bytearray(f.read())

    # Step 1: Check length > 512
    if len(raw) <= 512:
        print("[FAIL] File size <= 512")
        return -1
    print("[PASS] File size > 512:", len(raw))

    # Step 2: Decrypt header
    hdr = bytearray(raw[:0x40])
    jl_enc_cipher(hdr, 0, 0x40, key=0xFFFF)

    # Step 3: Check header CRC
    hdrcrc = struct.unpack_from('<H', hdr, 0)[0]
    calc_hdrcrc = jl_crc16(hdr[2:0x40])
    if hdrcrc != calc_hdrcrc:
        print(f"[FAIL] Header CRC mismatch: 0x{hdrcrc:04X} != 0x{calc_hdrcrc:04X}")
        return -2
    print(f"[PASS] Header CRC matches: 0x{hdrcrc:04X}")

    hdrcrc, listcrc, imgsize, numents, wa3, wa4, chipname = struct.unpack_from('<HHIHHI48s', hdr, 0)
    print(f"  imgsize: 0x{imgsize:X}, numents: {numents}, chip: {chipname.split(b'\\x00')[0]}")

    # Step 4: Check imgsize <= len(raw)
    if imgsize > len(raw):
        print(f"[FAIL] imgsize {imgsize} > raw {len(raw)}")
        return -3

    # Step 5: Check numents
    if numents > 0x100:
        print(f"[FAIL] Too many entries: {numents}")
        return -4

    # Step 6: Check entry table CRC
    headersize = 0x40 + numents * 0x50
    table_bytes = raw[0x40:headersize]
    calc_listcrc = jl_crc16(table_bytes)
    if listcrc != calc_listcrc:
        print(f"[FAIL] Entry table CRC mismatch: 0x{listcrc:04X} != 0x{calc_listcrc:04X}")
        return -5
    print(f"[PASS] Entry table CRC matches: 0x{listcrc:04X}")

    # Step 7: Decrypt entry table
    entry_blob = bytearray(table_bytes)
    for i in range(numents):
        jl_enc_cipher(entry_blob, i * 0x50, 0x50, key=0xFFFF)

    # Step 8: Find tail.bin
    tail_entry = None
    tail_idx = -1
    for i in range(numents):
        off = i * 0x50
        etype, eidx, edcrc, ewa1, eoffset, esize, esize2, ewa2, ename = struct.unpack_from('<HHHHIII44s16s', entry_blob, off)
        name = ename.split(b'\x00')[0]
        if etype == 0x00FF and name == b'tail.bin':
            tail_entry = (etype, eidx, edcrc, ewa1, eoffset, esize, esize2, ewa2, ename)
            tail_idx = i
            break

    if tail_entry is None:
        print("[FAIL] tail.bin entry not found!")
        return -6
    print(f"[PASS] tail.bin found at entry {tail_idx}: offset=0x{tail_entry[4]:X}, size={tail_entry[5]}")

    # Step 9: Verify tail.bin magic and CRC
    t_off = tail_entry[4]
    t_sz = tail_entry[5]
    tail_data = raw[t_off : t_off + t_sz]

    # Magic check: offset 0x22 is 13 92 65 36 73 42
    if tail_data[0x22:0x28] != b'\x13\x92e6sB':
        print(f"[FAIL] tail.bin magic mismatch: {tail_data[0x22:0x28].hex()}")
        return -7
    print(f"[PASS] tail.bin magic matched: {tail_data[0x22:0x28].hex()}")

    # tail[:0x20] CRC check
    tail_crc = jl_crc16(tail_data[:0x20])
    exp_tail_crc = struct.unpack_from('<H', tail_data, 0x20)[0]
    if tail_crc != exp_tail_crc:
        print(f"[FAIL] tail[:0x20] CRC mismatch: 0x{tail_crc:04X} != 0x{exp_tail_crc:04X}")
        return -8
    print(f"[PASS] tail[:0x20] CRC matched: 0x{tail_crc:04X}")

    # Step 10: Check each unencrypted entry CRC
    for i in range(numents):
        off = i * 0x50
        etype, eidx, edcrc, ewa1, eoffset, esize, esize2, ewa2, ename = struct.unpack_from('<HHHHIII44s16s', entry_blob, off)
        name = ename.split(b'\x00')[0].decode('latin1')
        val_18 = struct.unpack_from('<I', entry_blob, off + 0x18)[0]
        
        if esize == 0:
            print(f"  Entry {i} ({name}): size 0, skipped")
            continue
            
        payload = raw[eoffset : eoffset + esize]
        if val_18 == 0:
            calc_crc = jl_crc16(payload)
            if calc_crc != edcrc:
                print(f"[FAIL] Entry {i} ({name}) CRC mismatch: 0x{edcrc:04X} != calc 0x{calc_crc:04X}!")
                return -9
            else:
                print(f"[PASS] Entry {i} ({name}) CRC matched: 0x{edcrc:04X}")
        else:
            print(f"  Entry {i} ({name}) has val_18=0x{val_18:X} (encrypted in package)")

    print("[SUCCESS] All checks in jl_format_ufw::parse PASSED!")
    return 0

simulate_parse('extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw')
simulate_parse('kindle_click_jieli_port/firmware/IINE_Kindle_Click_Mod_v1.0.ufw')
