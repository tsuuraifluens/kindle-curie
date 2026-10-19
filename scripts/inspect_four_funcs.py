import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

fns = [0x004BB470, 0x004BA2A0, 0x004B9120, 0x004B8DB0]
for fn in fns:
    off = va_to_off(fn)
    print(f"\n================ Function at 0x{fn:08X} (off 0x{off:X}) ================")
    code = data[off : off + 500]
    for i in range(len(code)-5):
        b = code[i]
        va = fn + i
        if b == 0xE8:
            rel = struct.unpack_from('<i', code, i+1)[0]
            print(f"  0x{va:08X}: CALL 0x{va+5+rel:08X}")
        elif b in (0x68, 0xB8, 0xBA, 0xB9, 0xBB, 0xBE, 0xBF):
            imm = struct.unpack_from('<I', code, i+1)[0]
            v_off = va_to_off(imm)
            if v_off and 0 <= v_off < len(data) - 4:
                s = data[v_off : v_off + 40].split(b'\x00')[0]
                if len(s) >= 2 and all(0x20 <= c <= 0x7E for c in s):
                    print(f"  0x{va:08X}: IMM 0x{imm:08X} -> '{s.decode('latin1')}'")
