import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

va = 0x00A55430
off = va_to_off(va)
chunk = data[off : off + 0x80]

for i in range(0, len(chunk), 16):
    cur_va = va + i
    sub = chunk[i:i+16]
    print(f"0x{cur_va:08X}: {' '.join(f'{b:02x}' for b in sub)}")
