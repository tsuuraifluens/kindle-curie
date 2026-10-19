import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

start_va = 0x004BD820
end_va = 0x004BD8B0

start_off = va_to_off(start_va)
end_off = va_to_off(end_va)
chunk = data[start_off:end_off]

for i in range(0, len(chunk), 16):
    va = start_va + i
    sub = chunk[i:i+16]
    print(f"0x{va:08X}: {' '.join(f'{b:02x}' for b in sub)}")
