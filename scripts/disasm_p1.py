import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

def disasm_block(start_va, end_va):
    start_off = va_to_off(start_va)
    end_off = va_to_off(end_va)
    p = start_off
    while p < end_off:
        va = off_to_va(p)
        b0 = data[p]
        b1 = data[p+1] if p+1 < len(data) else 0
        
        # Check call
        if b0 == 0xE8:
            rel = struct.unpack_from('<i', data, p+1)[0]
            t = va + 5 + rel
            print(f"0x{va:08X}: call 0x{t:08X}")
            p += 5
            continue
        # Jumps
        if b0 == 0xE9:
            rel = struct.unpack_from('<i', data, p+1)[0]
            print(f"0x{va:08X}: jmp 0x{va+5+rel:08X}")
            p += 5
            continue
        if b0 == 0xEB:
            rel = struct.unpack_from('<b', data, p+1)[0]
            print(f"0x{va:08X}: jmp short 0x{va+2+rel:08X}")
            p += 2
            continue
        if b0 == 0x0F and 0x80 <= b1 <= 0x8F:
            rel = struct.unpack_from('<i', data, p+2)[0]
            cond = ['jo','jno','jb','jnb','je','jne','jbe','ja','js','jns','jp','jnp','jl','jge','jle','jg'][b1 - 0x80]
            print(f"0x{va:08X}: {cond} 0x{va+6+rel:08X}")
            p += 6
            continue
        if 0x70 <= b0 <= 0x7F:
            rel = struct.unpack_from('<b', data, p+1)[0]
            cond = ['jo','jno','jb','jnb','je','jne','jbe','ja','js','jns','jp','jnp','jl','jge','jle','jg'][b0 - 0x70]
            print(f"0x{va:08X}: {cond} 0x{va+2+rel:08X}")
            p += 2
            continue
        # String / imm pushes
        if b0 == 0x68:
            imm = struct.unpack_from('<I', data, p+1)[0]
            s_str = ""
            v_off = va_to_off(imm)
            if v_off and 0 <= v_off < len(data) - 4:
                s = data[v_off:v_off+30].split(b'\x00')[0]
                if len(s) >= 2 and all(0x20 <= c <= 0x7E for c in s):
                    s_str = f" -> '{s.decode('latin1')}'"
            print(f"0x{va:08X}: push 0x{imm:08X}{s_str}")
            p += 5
            continue
        # Print interesting opcodes
        h = ' '.join(f'{b:02x}' for b in data[p:min(p+7, len(data))])
        print(f"0x{va:08X}: {h}")
        p += 1

disasm_block(0x004BCB00, 0x004BCC80)
