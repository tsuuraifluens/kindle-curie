import struct
from re_meta import data, off_to_va, find_ptrs_to

for off in [0x929835, 0x9c6608, 0x9c65c4, 0x9c65dd, 0x9c65f5]:
    va = off_to_va(off)
    print(f"Off 0x{off:X} (VA 0x{va:X}):")
    ptrs = find_ptrs_to(va)
    for p_off, p_va in ptrs:
        print(f"  Ref at file 0x{p_off:X} (VA 0x{p_va:X})")
