import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off

start = va_to_off(0x0040F060)
code = data[start : start + 160]
print("Code at 0x0040F060:")
for i in range(0, len(code), 16):
    va = 0x0040F060 + i
    print(f"0x{va:08X}: {' '.join(f'{b:02x}' for b in code[i:i+16])}")
