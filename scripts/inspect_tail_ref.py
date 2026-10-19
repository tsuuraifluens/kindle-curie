import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off

def print_around(va):
    off = va_to_off(va)
    print(f"\nAround VA 0x{va:08X} (off 0x{off:X}):")
    code = data[off-32 : off+96]
    for i in range(0, len(code), 16):
        c_va = va - 32 + i
        print(f"0x{c_va:08X}: {' '.join(f'{b:02x}' for b in code[i:i+16])}")

print_around(0x00465378)
print_around(0x00465448)
