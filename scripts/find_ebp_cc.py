import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

start_va = 0x004BCA80
end_va = 0x004BD8A0
off1 = va_to_off(start_va)
off2 = va_to_off(end_va)
code = data[off1:off2]

print("Searching for [ebp - 0x34] (0xCC) in 0x004BCA80:")
for i in range(len(code)-3):
    va = start_va + i
    b0 = code[i]
    b1 = code[i+1]
    b2 = code[i+2]
    if (b0 in (0x8B, 0x8D, 0xC7, 0x89) and b1 in (0x45, 0x4D, 0x55, 0x5D, 0x75, 0x7D) and b2 == 0xCC):
        print(f"  0x{va:08X}: {b0:02X} {b1:02X} {b2:02X}")
