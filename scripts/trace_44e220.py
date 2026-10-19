import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

start_va = 0x0044E220
end_va = 0x0044E409

off = va_to_off(start_va)
p = off
end_off = va_to_off(end_va)

print(f"Tracing 0x0044E220:")
while p < end_off:
    va = off_to_va(p)
    b0 = data[p]
    b1 = data[p+1] if p+1 < len(data) else 0
    
    if b0 == 0xE8:
        rel = struct.unpack_from('<i', data, p+1)[0]
        tgt = va + 5 + rel
        print(f"0x{va:08X}: call 0x{tgt:08X}")
        p += 5
        continue
    if b0 in (0xC3, 0xC2):
        print(f"0x{va:08X}: ret")
        p += 1
        continue
    if b0 == 0xEB:
        rel = struct.unpack_from('<b', data, p+1)[0]
        print(f"0x{va:08X}: jmp short 0x{va+2+rel:08X}")
        p += 2
        continue
    if b0 == 0xE9:
        rel = struct.unpack_from('<i', data, p+1)[0]
        print(f"0x{va:08X}: jmp 0x{va+5+rel:08X}")
        p += 5
        continue
    if 0x70 <= b0 <= 0x7F:
        rel = struct.unpack_from('<b', data, p+1)[0]
        cond = ['jo','jno','jb','jnb','je','jne','jbe','ja','js','jns','jp','jnp','jl','jge','jle','jg'][b0 - 0x70]
        print(f"0x{va:08X}: {cond} 0x{va+2+rel:08X}")
        p += 2
        continue
    if b0 == 0x0F and 0x80 <= b1 <= 0x8F:
        rel = struct.unpack_from('<i', data, p+2)[0]
        cond = ['jo','jno','jb','jnb','je','jne','jbe','ja','js','jns','jp','jnp','jl','jge','jle','jg'][b1 - 0x80]
        print(f"0x{va:08X}: {cond} 0x{va+6+rel:08X}")
        p += 6
        continue
    if b0 == 0x68:
        imm = struct.unpack_from('<I', data, p+1)[0]
        v_off = va_to_off(imm)
        s_str = ""
        if v_off and 0 <= v_off < len(data) - 4:
            s = data[v_off : v_off + 30].split(b'\x00')[0]
            if len(s) >= 2 and all(0x20 <= c <= 0x7E for c in s):
                s_str = f" ('{s.decode('latin1')}')"
        print(f"0x{va:08X}: push 0x{imm:08X}{s_str}")
        p += 5
        continue
    p += 1
