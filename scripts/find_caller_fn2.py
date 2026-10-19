import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off

for va in [0x0041E388, 0x0041EA60]:
    off = va_to_off(va)
    for p in range(off, off - 1500, -1):
        if data[p:p+3] == b'\x55\x8b\xec' or data[p:p+3] == b'\x55\x89\xe5' or data[p-1:p+1] == b'\xcc\x55':
            print(f"Function containing 0x{va:08X} starts at file 0x{p:X} (VA 0x{off_to_va(p):08X})")
            break
