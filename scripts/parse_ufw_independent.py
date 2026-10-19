import struct, hashlib

def parse_ufw(filepath):
    with open(filepath, 'rb') as f:
        raw = f.read()

    print(f"=== Independent UFW Parser for {filepath} ===")
    print(f"File size: {len(raw)} bytes (0x{len(raw):X})")
    print(f"SHA-256:   {hashlib.sha256(raw).hexdigest().upper()}")

    # UFW Header: 0x40 (64 bytes)
    hdr = raw[:0x40]
    magic = hdr[0:4]
    hdr_crc = struct.unpack('<H', hdr[4:6])[0]
    ver = struct.unpack('<H', hdr[6:8])[0]
    flags = struct.unpack('<H', hdr[8:10])[0]
    entries_cnt = struct.unpack('<H', hdr[10:12])[0]
    chip_name = hdr[12:28].split(b'\x00')[0].decode('latin1', errors='replace')
    build_time = hdr[28:44].split(b'\x00')[0].decode('latin1', errors='replace')
    sdk_id = hdr[44:64].split(b'\x00')[0].decode('latin1', errors='replace')

    print("\n--- UFW Header (0x00 - 0x3F) ---")
    print(f"Magic:         {magic} (0x{magic.hex()})")
    print(f"Header CRC16:  0x{hdr_crc:04X}")
    print(f"Version:       0x{ver:04X}")
    print(f"Flags:         0x{flags:04X}")
    print(f"Entries Count: {entries_cnt}")
    print(f"Chip Name:     '{chip_name}'")
    print(f"Build Time:    '{build_time}'")
    print(f"SDK ID:        '{sdk_id}'")

    # Following the header is the entry table
    # Let's inspect bytes from 0x40 onwards
    print("\n--- Raw Bytes 0x40 to 0x140 ---")
    print(raw[0x40:0x140].hex(' '))

if __name__ == '__main__':
    parse_ufw('extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw')
