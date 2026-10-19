import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

def trace_calls(start_va, end_va):
    start_off = va_to_off(start_va)
    end_off = va_to_off(end_va)
    
    # We will walk bytes and identify CALL instructions (0xE8) and PUSH instructions right before them
    p = start_off
    while p < end_off:
        va = off_to_va(p)
        if data[p] == 0xE8:
            rel = struct.unpack_from('<i', data, p+1)[0]
            target_va = va + 5 + rel
            
            # Look backwards up to 30 bytes for pushes or moves
            context = []
            bp = max(start_off, p - 32)
            # Find pushes
            sub = data[bp:p]
            for j in range(len(sub)):
                c_va = off_to_va(bp + j)
                if sub[j] == 0x68: # push imm32
                    imm = struct.unpack_from('<I', sub, j+1)[0]
                    # check if string
                    imm_off = va_to_off(imm)
                    s_str = ""
                    if imm_off and 0 <= imm_off < len(data) - 4:
                        s = data[imm_off:imm_off+30].split(b'\x00')[0]
                        if len(s) >= 2 and all(0x20 <= c <= 0x7E for c in s):
                            s_str = f" ('{s.decode('latin1')}')"
                    context.append(f"push 0x{imm:08X}{s_str}")
                elif sub[j] == 0x6A: # push imm8
                    imm8 = sub[j+1]
                    context.append(f"push 0x{imm8:02X}")
            ctx_str = ", ".join(context[-4:]) if context else ""
            print(f"0x{va:08X}: CALL 0x{target_va:08X} | args: [{ctx_str}]")
            p += 5
            continue
        p += 1

print("Calls in 0x004BCA80:")
trace_calls(0x004BCB00, 0x004BDA30)
