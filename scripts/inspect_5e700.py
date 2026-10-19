import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off

start = va_to_off(0x0045E700)
print(data[start : start + 100].hex(' '))

# Also let's check what strings are referenced in 0x0045E700
for p in range(start, start + 300):
    val = int.from_bytes(data[p:p+4], 'little')
    v_off = va_to_off(val)
    if v_off and 0 <= v_off < len(data) - 4:
        s = data[v_off:v_off+40].split(b'\x00')[0]
        if len(s) >= 2 and all(0x20 <= c <= 0x7E for c in s):
            print(f"  Ref at 0x{off_to_va(p):08X}: 0x{val:08X} '{s.decode('latin1')}'")
