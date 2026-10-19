import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

start_va = 0x004B8000
end_va = 0x004BBC00

off1 = va_to_off(start_va)
off2 = va_to_off(end_va)
sub = data[off1:off2]

print(f"Searching string pointers in [0x{start_va:08X}, 0x{end_va:08X}]:")
for i in range(len(sub)-4):
    imm = struct.unpack_from('<I', sub, i)[0]
    v_off = va_to_off(imm)
    if v_off and 0 <= v_off < len(data) - 4:
        s = data[v_off : v_off + 40].split(b'\x00')[0]
        if len(s) >= 3 and all(0x20 <= c <= 0x7E for c in s):
            va = start_va + i
            print(f"  0x{va:08X} -> 0x{imm:08X}: '{s.decode('latin1')}'")
