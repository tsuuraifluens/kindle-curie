import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

start_va = 0x00A55600
end_va = 0x00A55650

off = va_to_off(start_va)
p = off
end_off = va_to_off(end_va)

chunk = data[off:end_off]
for i in range(0, len(chunk), 16):
    cur_va = start_va + i
    sub = chunk[i:i+16]
    print(f"0x{cur_va:08X}: {' '.join(f'{b:02x}' for b in sub)}")
