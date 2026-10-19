import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

start_va = 0x0040E96C
end_va = 0x0040F0A0
off = va_to_off(start_va)
end_off = va_to_off(end_va)

code = data[off:end_off]
p = 0
while p < len(code) - 6:
    va = start_va + p
    b0 = code[p]
    b1 = code[p+1]
    
    if b0 == 0xE8:
        rel = struct.unpack_from('<i', code, p+1)[0]
        tgt = va + 5 + rel
        print(f"0x{va:08X}: call 0x{tgt:08X}")
        p += 5
        continue
    if b0 == 0x68:
        imm = struct.unpack_from('<I', code, p+1)[0]
        s_str = ''
        v_off = va_to_off(imm)
        if v_off and 0 <= v_off < len(data) - 4:
            s = data[v_off : v_off + 30].split(b'\x00')[0]
            if len(s) >= 2 and all(0x20 <= c <= 0x7E for c in s):
                s_str = f' ("{s.decode("latin1")}")'
        print(f"0x{va:08X}: push 0x{imm:08X}{s_str}")
        p += 5
        continue
    if b0 in (0x85, 0x83, 0x3B, 0x39, 0x80, 0x81):
        chunk = code[p:p+4]
        if chunk[:2] == b'\x85\xc0':
            print(f"0x{va:08X}: test eax, eax")
            p += 2
            continue
    if 0x70 <= b0 <= 0x7F:
        rel = struct.unpack_from('<b', code, p+1)[0]
        cond = ['jo','jno','jb','jnb','je','jne','jbe','ja','js','jns','jp','jnp','jl','jge','jle','jg'][b0 - 0x70]
        print(f"0x{va:08X}: {cond} 0x{va+2+rel:08X}")
        p += 2
        continue
    if b0 == 0x0F and 0x80 <= b1 <= 0x8F:
        rel = struct.unpack_from('<i', code, p+2)[0]
        cond = ['jo','jno','jb','jnb','je','jne','jbe','ja','js','jns','jp','jnp','jl','jge','jle','jg'][b1 - 0x80]
        print(f"0x{va:08X}: {cond} 0x{va+6+rel:08X}")
        p += 6
        continue
    if b0 == 0xE9:
        rel = struct.unpack_from('<i', code, p+1)[0]
        print(f"0x{va:08X}: jmp 0x{va+5+rel:08X}")
        p += 5
        continue
    if b0 == 0xEB:
        rel = struct.unpack_from('<b', code, p+1)[0]
        print(f"0x{va:08X}: jmp short 0x{va+2+rel:08X}")
        p += 2
        continue
    if b0 in (0xC3, 0xC2):
        print(f"0x{va:08X}: ret")
        p += (3 if b0 == 0xC2 else 1)
        continue
    p += 1
