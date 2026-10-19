import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off

for va in [0x0040D3B1, 0x0041E388, 0x0041EA60]:
    off = va_to_off(va)
    print(f"\n==========================================")
    print(f"Around VA 0x{va:08X} (file 0x{off:X}):")
    # print 128 bytes before and 64 bytes after
    start = off - 96
    code = data[start : off + 64]
    for i in range(0, len(code), 16):
        c_va = off_to_va(start + i)
        print(f"  0x{c_va:08X}: {' '.join(f'{b:02x}' for b in code[i:i+16])}")
