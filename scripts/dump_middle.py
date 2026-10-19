import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

start_va = 0x00465CA0
end_va = 0x00465E50
start_off = va_to_off(start_va)
end_off = va_to_off(end_va)
code = data[start_off:end_off]

print(f"Code from 0x{start_va:08X} to 0x{end_va:08X}:")
for i in range(0, len(code), 16):
    va = start_va + i
    chunk = code[i:i+16]
    print(f"0x{va:08X}: {' '.join(f'{b:02x}' for b in chunk)}")
