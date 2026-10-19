import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

target = 0x00D2B5DF
for i in range(0, 0x927A00, 4):
    val = struct.unpack_from('<I', data, i)[0]
    if val == target:
        va = off_to_va(i)
        print(f"Reference at file 0x{i:X} (VA {hex(va) if va else 'None'})")
