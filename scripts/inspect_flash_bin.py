import struct, hashlib, sys, os
sys.path.insert(0, os.path.abspath('scripts/jieli_tools'))
from jltech.cipher import jl_enc_cipher, jl_sfc_cipher
from jltech.crc import jl_crc16

def inspect_flash_bin():
    with open('extracted/L1161兼容阅星瞳/L1161 L1162 IINE-reader.ufw', 'rb') as f:
        f.seek(0x400)
        flash_data = bytearray(f.read(0x34000))

    print(f"=== Flash.bin Inspection (212992 bytes, 0x34000) ===")
    print(f"SHA-256 (scrambled): {hashlib.sha256(flash_data).hexdigest().upper()}")

    # The first 32 bytes of Flash.bin:
    print("\n--- First 32 bytes (Raw Flash 0x000000) ---")
    print(flash_data[:32].hex(' '))

    # Descramble header with key 0xFFFF
    hdr_copy = bytearray(flash_data[:32])
    jl_enc_cipher(hdr_copy, 0, 32, key=0xFFFF)
    print("\n--- First 32 bytes descrambled (Key 0xFFFF) ---")
    print(hdr_copy.hex(' '))
    hcrc = int.from_bytes(hdr_copy[:2], 'little')
    calc_crc = jl_crc16(hdr_copy[2:32])
    print(f"Header CRC: 0x{hcrc:04X} (calc: 0x{calc_crc:04X})")

    # In JLFS, flash header format:
    # <HH4sIBBBB16s
    # hcrc, unk1, magic, flash_size, align, fs_ver, opt, unk2, pid
    hcrc, unk1, magic, flash_size, align, fs_ver, opt, unk2, pid = struct.unpack('<HH4sIBBBB16s', hdr_copy)
    print(f"Magic:       {magic}")
    print(f"Flash Size:  0x{flash_size:X} ({flash_size} bytes = {flash_size//1024} KB)")
    print(f"Alignment:   {align}")
    print(f"FS Version:  {fs_ver}")
    print(f"PID:         {pid}")

if __name__ == '__main__':
    inspect_flash_bin()
