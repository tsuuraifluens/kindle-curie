import struct, sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off

target_va = 0x00493E40
calls = []
for i in range(len(data) - 5):
    if data[i] == 0xE8:
        rel = struct.unpack_from('<i', data, i + 1)[0]
        cur_va = off_to_va(i)
        if cur_va and cur_va + 5 + rel == target_va:
            calls.append((i, cur_va))

print(f"Calls to 0x{target_va:08X}:")
for c_off, c_va in calls:
    print(f"  file 0x{c_off:X} (VA 0x{c_va:08X})")
