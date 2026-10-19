import struct, sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off

start = va_to_off(0x0049B090)
print(f"static_metacall at 0x0049B090 (off 0x{start:X}):")
code = data[start : start + 300]
print(code[:100].hex(' '))

# Find jump table or switch
for i in range(len(code) - 4):
    val = struct.unpack_from('<I', code, i)[0]
    off = va_to_off(val)
    if off and 0x400 <= off < 0x927A00:
        vas = struct.unpack_from('<21I', data, off)
        if all(0x401000 <= v < 0xD29000 for v in vas):
            print(f"FOUND JUMP TABLE at VA 0x{val:08X} (file offset 0x{off:X})")
            for idx, v in enumerate(vas):
                print(f"  case {idx}: VA 0x{v:08X}")
            break
