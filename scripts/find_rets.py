import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

start_va = 0x004BCA80
end_va = 0x004BDA80

start_off = va_to_off(start_va)
end_off = va_to_off(end_va)
code = data[start_off:end_off]

print(f"Analyzing returns and jumps in [0x{start_va:08X}, 0x{end_va:08X}]:")

# Find all occurrences of ret (0xC2, 0xC3)
for i in range(len(code)):
    va = start_va + i
    b = code[i]
    if b in (0xC3, 0xC2):
        # Look back 15 bytes
        lookback = code[max(0, i-20):i]
        print(f"RET at 0x{va:08X}: prev bytes: {' '.join(f'{x:02x}' for x in lookback)}")
