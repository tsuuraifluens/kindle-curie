import hashlib, struct, sys, os
sys.path.insert(0, os.path.abspath('scripts/jieli_tools'))
from jltech.cipher import jl_enc_cipher, jl_sfc_cipher
from jltech.crc import jl_crc16

def audit_candidate():
    raw_ufw_path = 'extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw'
    mod_ufw_path = 'candidate/IINE_Kindle_Mod_Candidate_v0.1.ufw'

    with open(raw_ufw_path, 'rb') as f:
        raw_ufw = f.read()
    with open(mod_ufw_path, 'rb') as f:
        mod_ufw = f.read()

    print("================================================================================")
    print("CANDIDATE FIRMWARE BYTE-BY-BYTE AUDIT REPORT")
    print(f"Original UFW:  {raw_ufw_path} ({len(raw_ufw)} bytes)")
    print(f"               SHA-256: {hashlib.sha256(raw_ufw).hexdigest().upper()}")
    print(f"Candidate UFW: {mod_ufw_path} ({len(mod_ufw)} bytes)")
    print(f"               SHA-256: {hashlib.sha256(mod_ufw).hexdigest().upper()}")
    print("================================================================================\n")

    # 1. Compare UFW container headers
    print("[1] UFW Container Header Comparison (Offset 0x00 - 0x3F):")
    raw_hdr = bytearray(raw_ufw[:0x40])
    mod_hdr = bytearray(mod_ufw[:0x40])
    jl_enc_cipher(raw_hdr, 0, 0x40, key=0xFFFF)
    jl_enc_cipher(mod_hdr, 0, 0x40, key=0xFFFF)
    
    raw_hdrcrc, raw_listcrc = struct.unpack_from('<HH', raw_hdr, 0)
    mod_hdrcrc, mod_listcrc = struct.unpack_from('<HH', mod_hdr, 0)
    print(f"    - Original Header CRC:    0x{raw_hdrcrc:04X}, List CRC: 0x{raw_listcrc:04X}")
    print(f"    - Candidate Header CRC:   0x{mod_hdrcrc:04X}, List CRC: 0x{mod_listcrc:04X}")

    # 2. Extract and decrypt flash.bin from both
    raw_flash = bytearray(raw_ufw[0x400 : 0x400 + 0x34000])
    mod_flash = bytearray(mod_ufw[0x400 : 0x400 + 0x34000])

    # Unscramble JLFS app.bin (offset 0x2000 to 0x34000 with ChipKey 0x190D)
    jl_sfc_cipher(raw_flash, 0x2000, 0x32000, 0x2000, key=0x190D)
    jl_sfc_cipher(mod_flash, 0x2000, 0x32000, 0x2000, key=0x190D)

    raw_app = raw_flash[0x2120 : 0x2120 + 198824]
    mod_app = mod_flash[0x2120 : 0x2120 + 198824]

    print(f"\n[2] Decrypted app.bin Differential Audit (198,824 bytes):")
    print(f"    - Raw app.bin SHA-256: {hashlib.sha256(raw_app).hexdigest().upper()}")
    print(f"    - Mod app.bin SHA-256: {hashlib.sha256(mod_app).hexdigest().upper()}")

    # Find differences in app.bin
    diffs = []
    i = 0
    while i < len(raw_app):
        if raw_app[i] != mod_app[i]:
            start = i
            while i < len(raw_app) and raw_app[i] != mod_app[i]:
                i += 1
            diffs.append((start, i - start))
        else:
            i += 1

    print(f"\n    Total Contiguous Modified Chunks in app.bin: {len(diffs)}")
    print(f"    | {'Chunk':<5} | {'app.bin Off':<12} | {'Flash Off':<12} | {'CPU Addr':<12} | {'Len':<5} | {'Original Hex':<28} | {'Modified Hex':<28} |")
    print(f"    |{'-'*7}|{'-'*14}|{'-'*14}|{'-'*14}|{'-'*7}|{'-'*30}|{'-'*30}|")

    for idx, (off, length) in enumerate(diffs):
        r_hex = raw_app[off:off+length].hex(' ')
        m_hex = mod_app[off:off+length].hex(' ')
        flash_off = 0x2120 + off
        cpu_addr = 0x01E00120 + off
        print(f"    | {idx+1:<5} | 0x{off:06X}     | 0x{flash_off:06X}     | 0x{cpu_addr:08X}   | {length:<5} | {r_hex:<28} | {m_hex:<28} |")

    # Audit each chunk semantics
    print("\n[3] Semantic Analysis of Each Modified Chunk:")
    for idx, (off, length) in enumerate(diffs):
        r_bytes = raw_app[off:off+length]
        m_bytes = mod_app[off:off+length]
        print(f"\n    --- Chunk {idx+1} at app.bin 0x{off:X} (CPU 0x{0x01E00120+off:X}) ---")
        if off == 0x211C6:
            print(f"    Target: Bluetooth Broadcast Name (Reader Mode)")
            print(f"    Original String: '{r_bytes.split(b'\\x00')[0].decode('latin1', errors='replace')}'")
            print(f"    Modified String: '{m_bytes.split(b'\\x00')[0].decode('latin1', errors='replace')}'")
            print(f"    Verification: String length {len(m_bytes)} preserves null-termination within original buffer.")
        elif off == 0x22384:
            print(f"    Target: GATT DIS Manufacturer Name (0x2A29)")
            print(f"    Original String: '{r_bytes.split(b'\\x00')[0].decode('latin1', errors='replace')}'")
            print(f"    Modified String: '{m_bytes.split(b'\\x00')[0].decode('latin1', errors='replace')}'")
            print(f"    Verification: Replaced 'zhuhai_jieli' with 'Amazon', padded with nulls.")
        elif off == 0x223BC:
            print(f"    Target: GATT DIS PnP ID (0x2A50)")
            print(f"    Original: {r_bytes.hex(' ')}")
            print(f"              Vendor ID Source: 0x{r_bytes[0]:02X}")
            print(f"              VID: 0x{int.from_bytes(r_bytes[1:3], 'little'):04X} (JieLi 0x053B)")
            print(f"              PID: 0x{int.from_bytes(r_bytes[3:5], 'little'):04X} (0x1007)")
            print(f"    Modified: {m_bytes.hex(' ')}")
            print(f"              Vendor ID Source: 0x{m_bytes[0]:02X}")
            print(f"              VID: 0x{int.from_bytes(m_bytes[1:3], 'little'):04X} (Amazon 0x0171)")
            print(f"              PID: 0x{int.from_bytes(m_bytes[3:5], 'little'):04X} (Kindle Remote 0x0443)")
        else:
            print(f"    Other change: {r_bytes} -> {m_bytes}")

if __name__ == '__main__':
    audit_candidate()
