import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off

start = va_to_off(0x0041D220)
end = va_to_off(0x0041E970)

for i in range(start, end):
    if data[i] in (0x68, 0xB8, 0xBA, 0xB9, 0xBB, 0xBE, 0xBF):
        val = int.from_bytes(data[i+1:i+5], 'little')
        val_off = va_to_off(val)
        if val_off:
            s = data[val_off:val_off+40].split(b'\x00')[0]
            if len(s) >= 2 and all(0x20 <= c <= 0x7E for c in s):
                print(f"  VA 0x{off_to_va(i):08X} references '{s.decode('latin1')}'")
