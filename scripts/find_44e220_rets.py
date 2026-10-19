import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

start_va = 0x0044E220
end_va = 0x0044E900

off = va_to_off(start_va)
end_off = va_to_off(end_va)
p = off

print("Scanning 0x0044E220 for returns:")
while p < end_off:
    va = off_to_va(p)
    b0 = data[p]
    if b0 in (0xC3, 0xC2):
        print(f"RET at 0x{va:08X}, prev bytes: {' '.join(f'{b:02x}' for b in data[p-10:p])}")
    p += 1
