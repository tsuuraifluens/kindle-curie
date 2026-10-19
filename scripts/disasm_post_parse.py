import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

start_va = 0x0040ECDD
end_va = 0x0040EE80
off = va_to_off(start_va)
end_off = va_to_off(end_va)
code = data[off:end_off]

p = 0
while p < len(code):
    va = start_va + p
    b = code[p]
    if b == 0xE8 and p + 5 <= len(code):
        rel = struct.unpack_from('<i', code, p+1)[0]
        tgt = va + 5 + rel
        print(f"0x{va:08X}: call 0x{tgt:08X}")
        p += 5
        continue
    elif b == 0x68 and p + 5 <= len(code):
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
    elif b in (0x74, 0x75, 0x7C, 0x7D, 0x7E, 0x7F, 0xEB) and p + 2 <= len(code):
        rel = struct.unpack_from('<b', code, p+1)[0]
        cond = {0x74:'je', 0x75:'jne', 0x7C:'jl', 0x7D:'jge', 0x7E:'jle', 0x7F:'jg', 0xEB:'jmp'}[b]
        print(f"0x{va:08X}: {cond} 0x{va+2+rel:08X}")
        p += 2
        continue
    elif b == 0x0F and p + 6 <= len(code) and 0x80 <= code[p+1] <= 0x8F:
        rel = struct.unpack_from('<i', code, p+2)[0]
        cond = ['jo','jno','jb','jnb','je','jne','jbe','ja','js','jns','jp','jnp','jl','jge','jle','jg'][code[p+1] - 0x80]
        print(f"0x{va:08X}: {cond} 0x{va+6+rel:08X}")
        p += 6
        continue
    p += 1
