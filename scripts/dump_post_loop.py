import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off

start = va_to_off(0x004661A2)
end = va_to_off(0x00466275)
code = data[start:end]

print("Hex dump of 0x004661A2..0x00466275:")
for i in range(0, len(code), 16):
    va = 0x004661A2 + i
    print(f"0x{va:08X}: {' '.join(f'{b:02x}' for b in code[i:i+16])}")
