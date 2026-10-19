import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

def analyze_func(fn_va, max_len=1000):
    print(f"\n=======================================================")
    print(f"Analyzing Mode Function at 0x{fn_va:08X}")
    print(f"=======================================================")
    off = va_to_off(fn_va)
    code = data[off : off + max_len]
    
    p = 0
    while p < len(code) - 6:
        va = fn_va + p
        b0 = code[p]
        b1 = code[p+1]
        
        if b0 == 0xE8:
            rel = struct.unpack_from('<i', code, p+1)[0]
            tgt = va + 5 + rel
            print(f"  0x{va:08X}: CALL 0x{tgt:08X}")
            p += 5
            continue
        if b0 == 0x68: # push imm32
            imm = struct.unpack_from('<I', code, p+1)[0]
            v_off = va_to_off(imm)
            s_str = ""
            if v_off and 0 <= v_off < len(data) - 4:
                s = data[v_off:v_off+40].split(b'\x00')[0]
                if len(s) >= 2 and all(0x20 <= c <= 0x7E for c in s):
                    s_str = f" -> '{s.decode('latin1')}'"
            print(f"  0x{va:08X}: push 0x{imm:08X}{s_str}")
            p += 5
            continue
        if b0 in (0xC3, 0xC2):
            imm = struct.unpack_from('<H', code, p+1)[0] if b0 == 0xC2 else 0
            print(f"  0x{va:08X}: RET {imm if b0 == 0xC2 else ''}")
            if p > 50:
                break
            p += (3 if b0 == 0xC2 else 1)
            continue
        p += 1

analyze_func(0x004BB470, 800)
analyze_func(0x004BA2A0, 800)
analyze_func(0x004B9120, 800)
analyze_func(0x004B8DB0, 800)
