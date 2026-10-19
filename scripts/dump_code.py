import sys, os
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off

start = va_to_off(0x004BCA80)
end = va_to_off(0x004BCB30)
code = data[start:end]

print("Hex dump of 0x004BCA80 to 0x004BCB30:")
for i in range(0, len(code), 16):
    chunk = code[i:i+16]
    va = 0x004BCA80 + i
    hex_str = ' '.join(f'{b:02x}' for b in chunk)
    print(f"0x{va:08X}: {hex_str}")
