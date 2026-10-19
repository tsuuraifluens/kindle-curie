import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

start_va = 0x004BCB70
off = va_to_off(start_va)
chunk = data[off : off + 0x60]
for i in range(0, len(chunk), 16):
    cur_va = start_va + i
    sub = chunk[i:i+16]
    print(f"0x{cur_va:08X}: {' '.join(f'{b:02x}' for b in sub)}")
