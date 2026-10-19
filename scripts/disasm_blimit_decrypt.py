import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

start_va = 0x00439C60
off = va_to_off(start_va)
code = data[off : off + 200]
p = 0
while p < len(code) - 6:
    va = start_va + p
    b = code[p]
    if b == 0xE8:
        rel = struct.unpack_from('<i', code, p+1)[0]
        tgt = va + 5 + rel
        print(f"0x{va:08X}: call 0x{tgt:08X}")
        p += 5
        continue
    if b == 0x68:
        imm = struct.unpack_from('<I', code, p+1)[0]
        v_off = va_to_off(imm)
        s_str = ''
        if v_off and 0 <= v_off < len(data) - 4:
            s = data[v_off:v_off+30].split(b'\x00')[0]
            if len(s) >= 2 and all(0x20 <= c <= 0x7E for c in s):
                s_str = f' -> "{s.decode("latin1")}"'
        print(f"0x{va:08X}: push 0x{imm:08X}{s_str}")
        p += 5
        continue
    p += 1
