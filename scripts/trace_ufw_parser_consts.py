import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

# Let's inspect the code bytes of 0x00465AF0 to 0x00466000
# and trace all cmp, test, call, jmp, lea, mov instructions!
start_va = 0x00465AF0
end_va = 0x00466005
start_off = va_to_off(start_va)
end_off = va_to_off(end_va)

# Let's see what strings are referenced in this function
print("=== String / Const references in 0x00465AF0..0x00466005 ===")
for p in range(start_off, end_off - 4):
    val = struct.unpack_from('<I', data, p)[0]
    val_off = va_to_off(val)
    if val_off and 0 <= val_off < len(data) - 4:
        s = data[val_off:val_off+40].split(b'\x00')[0]
        if len(s) >= 2 and all(0x20 <= c <= 0x7E for c in s):
            cur_va = off_to_va(p)
            print(f"  0x{cur_va:08X} (p=0x{p:X}) -> 0x{val:08X}: '{s.decode('latin1')}'")
