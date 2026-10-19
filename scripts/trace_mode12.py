import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

def trace_func_instructions(fn_va, count=100):
    off = va_to_off(fn_va)
    p = off
    print(f"\nTracing instructions for 0x{fn_va:08X}:")
    for _ in range(count):
        va = off_to_va(p)
        b = data[p]
        # Check call
        if b == 0xE8:
            rel = struct.unpack_from('<i', data, p+1)[0]
            tgt = va + 5 + rel
            print(f"  0x{va:08X}: CALL 0x{tgt:08X}")
            p += 5
            continue
        if b in (0xC3, 0xC2):
            imm = struct.unpack_from('<H', data, p+1)[0] if b == 0xC2 else 0
            print(f"  0x{va:08X}: RET {imm if b == 0xC2 else ''}")
            break
        if b == 0x68:
            imm = struct.unpack_from('<I', data, p+1)[0]
            s_str = ""
            v_off = va_to_off(imm)
            if v_off and 0 <= v_off < len(data) - 4:
                s = data[v_off : v_off + 30].split(b'\x00')[0]
                if len(s) >= 2 and all(0x20 <= c <= 0x7E for c in s):
                    s_str = f" ('{s.decode('latin1')}')"
            print(f"  0x{va:08X}: PUSH 0x{imm:08X}{s_str}")
            p += 5
            continue
        if b == 0x6A:
            print(f"  0x{va:08X}: PUSH 0x{data[p+1]:02X}")
            p += 2
            continue
        # Jumps
        if b == 0xEB:
            rel = struct.unpack_from('<b', data, p+1)[0]
            print(f"  0x{va:08X}: JMP short 0x{va+2+rel:08X}")
            p += 2
            continue
        if b == 0xE9:
            rel = struct.unpack_from('<i', data, p+1)[0]
            print(f"  0x{va:08X}: JMP 0x{va+5+rel:08X}")
            p += 5
            continue
        if 0x70 <= b <= 0x7F:
            rel = struct.unpack_from('<b', data, p+1)[0]
            cond = ['jo','jno','jb','jnb','je','jne','jbe','ja','js','jns','jp','jnp','jl','jge','jle','jg'][b - 0x70]
            print(f"  0x{va:08X}: {cond} 0x{va+2+rel:08X}")
            p += 2
            continue
        if b == 0x0F and 0x80 <= data[p+1] <= 0x8F:
            rel = struct.unpack_from('<i', data, p+2)[0]
            cond = ['jo','jno','jb','jnb','je','jne','jbe','ja','js','jns','jp','jnp','jl','jge','jle','jg'][data[p+1] - 0x80]
            print(f"  0x{va:08X}: {cond} 0x{va+6+rel:08X}")
            p += 6
            continue
        # Mov / cmp
        h = ' '.join(f'{x:02x}' for x in data[p:p+4])
        print(f"  0x{va:08X}: {h}")
        p += 1

trace_func_instructions(0x004BB470, 80)
trace_func_instructions(0x004BA2A0, 80)
