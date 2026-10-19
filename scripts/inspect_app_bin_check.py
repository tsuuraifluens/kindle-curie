import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

def dump_range(start_va, end_va):
    print(f"\n--- Code from 0x{start_va:08X} to 0x{end_va:08X} ---")
    off = va_to_off(start_va)
    sz = end_va - start_va
    chunk = data[off:off+sz]
    p = 0
    while p < len(chunk) - 5:
        va = start_va + p
        b0 = chunk[p]
        if b0 == 0xE8:
            rel = struct.unpack_from('<i', chunk, p+1)[0]
            print(f"  0x{va:08X}: CALL 0x{va+5+rel:08X}")
            p += 5
            continue
        if b0 == 0x68:
            imm = struct.unpack_from('<I', chunk, p+1)[0]
            v_off = va_to_off(imm)
            s_str = ""
            if v_off and 0 <= v_off < len(data) - 4:
                s = data[v_off : v_off + 30].split(b'\x00')[0]
                if len(s) >= 2 and all(0x20 <= c <= 0x7E for c in s):
                    s_str = f" -> '{s.decode('latin1')}'"
            print(f"  0x{va:08X}: PUSH 0x{imm:08X}{s_str}")
            p += 5
            continue
        p += 1

dump_range(0x004BA000, 0x004BA150)
dump_range(0x004BB1E0, 0x004BB300)
