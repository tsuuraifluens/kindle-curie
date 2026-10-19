import struct, sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off

start_va = 0x004BCA80
start_off = va_to_off(start_va)
# Let's inspect the first 250 bytes of 0x004BCA80
code = data[start_off : start_off + 250]

for i in range(0, len(code), 16):
    va = start_va + i
    chunk = code[i:i+16]
    print(f"0x{va:08X}: {' '.join(f'{b:02x}' for b in chunk)}")
