import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

start_va = 0x00438190
off = va_to_off(start_va)
code = data[off : off + 500]

print(f"Code for byteArrayDataToKey (VA 0x{start_va:08X}):")
for i in range(0, len(code), 16):
    va = start_va + i
    chunk = code[i:i+16]
    print(f"0x{va:08X}: {' '.join(f'{b:02x}' for b in chunk)}")
