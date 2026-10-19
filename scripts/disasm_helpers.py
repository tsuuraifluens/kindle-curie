import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

def disasm_f(f_va, sz=300):
    off = va_to_off(f_va)
    code = data[off:off+sz]
    p = 0
    while p < len(code) - 6:
        va = f_va + p
        b0 = code[p]
        if b0 == 0xE8:
            rel = struct.unpack_from('<i', code, p+1)[0]
            tgt = va + 5 + rel
            print(f"0x{va:08X}: call 0x{tgt:08X}")
            p += 5
            continue
        if b0 == 0x68:
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

print('--- 0x0041CB00 ---')
disasm_f(0x0041CB00)
print('--- 0x004B8880 ---')
disasm_f(0x004B8880)
