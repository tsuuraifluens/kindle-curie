import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

start_va = 0x004BCA80
end_va = 0x004BDA80

start_off = va_to_off(start_va)
end_off = va_to_off(end_va)
code = data[start_off:end_off]

print("Searching for jumps targeting 0x004BDA40 - 0x004BDA76:")
for i in range(len(code)-6):
    va = start_va + i
    b0 = code[i]
    b1 = code[i+1]
    
    tgt = None
    jmp_type = None
    if b0 == 0xE9:
        rel = struct.unpack_from('<i', code, i+1)[0]
        tgt = va + 5 + rel
        jmp_type = "jmp"
    elif b0 == 0xEB:
        rel = struct.unpack_from('<b', code, i+1)[0]
        tgt = va + 2 + rel
        jmp_type = "jmp short"
    elif b0 == 0x0F and 0x80 <= b1 <= 0x8F:
        rel = struct.unpack_from('<i', code, i+2)[0]
        tgt = va + 6 + rel
        cond = ['jo','jno','jb','jnb','je','jne','jbe','ja','js','jns','jp','jnp','jl','jge','jle','jg'][b1 - 0x80]
        jmp_type = cond
    elif 0x70 <= b0 <= 0x7F:
        rel = struct.unpack_from('<b', code, i+1)[0]
        tgt = va + 2 + rel
        cond = ['jo','jno','jb','jnb','je','jne','jbe','ja','js','jns','jp','jnp','jl','jge','jle','jg'][b0 - 0x70]
        jmp_type = cond
        
    if tgt is not None and 0x004BDA30 <= tgt <= 0x004BDA76:
        print(f"0x{va:08X}: {jmp_type} 0x{tgt:08X}")
