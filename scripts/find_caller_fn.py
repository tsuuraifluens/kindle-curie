import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off

# Let's find function start before 0x0040D3B1
off = va_to_off(0x0040D3B1)
# Search backward for standard prologue: 55 8b ec or 55 89 e5 or similar
for p in range(off, off - 1000, -1):
    if data[p:p+3] == b'\x55\x8b\xec' or data[p:p+3] == b'\x55\x89\xe5':
        print(f"Function start at file 0x{p:X} (VA 0x{off_to_va(p):08X})")
        break
    elif data[p-1:p+1] == b'\xcc\x55':
        print(f"Function start at file 0x{p:X} (VA 0x{off_to_va(p):08X})")
        break

# Also let's check what strings are referenced in 0x0040D000 - 0x0040E000
for i in range(va_to_off(0x0040D000), va_to_off(0x0040DF00)):
    if data[i] in (0x68, 0xB8, 0xBA, 0xB9, 0xBB, 0xBE, 0xBF):
        val = int.from_bytes(data[i+1:i+5], 'little')
        val_off = va_to_off(val)
        if val_off:
            s = data[val_off:val_off+30].split(b'\x00')[0]
            if len(s) >= 3 and all(0x20 <= c <= 0x7E for c in s):
                print(f"  VA 0x{off_to_va(i):08X} references '{s.decode('latin1')}'")
