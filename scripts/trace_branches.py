import struct, sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off

# Let's inspect the exact bytes and control flow between 0x00465B40 and 0x00465E50
start_va = 0x00465B40
end_va = 0x00466000
start_off = va_to_off(start_va)
end_off = va_to_off(end_va)

code = data[start_off:end_off]

# Let's dump all instructions with simple decoding
p = 0
while p < len(code):
    cur_va = start_va + p
    b0 = code[p]
    b1 = code[p+1] if p+1 < len(code) else 0
    b2 = code[p+2] if p+2 < len(code) else 0
    b3 = code[p+3] if p+3 < len(code) else 0
    
    # Check conditional jumps
    if b0 == 0x0F and 0x80 <= b1 <= 0x8F:
        rel = struct.unpack_from('<i', code, p+2)[0]
        cond = ['jo','jno','jb','jnb','je','jne','jbe','ja','js','jns','jp','jnp','jl','jge','jle','jg'][b1 - 0x80]
        t = cur_va + 6 + rel
        print(f"0x{cur_va:08X}: {cond} 0x{t:08X}")
        p += 6
        continue
    if 0x70 <= b0 <= 0x7F:
        rel = struct.unpack_from('<b', code, p+1)[0]
        cond = ['jo','jno','jb','jnb','je','jne','jbe','ja','js','jns','jp','jnp','jl','jge','jle','jg'][b0 - 0x70]
        t = cur_va + 2 + rel
        print(f"0x{cur_va:08X}: {cond} 0x{t:08X}")
        p += 2
        continue
    if b0 == 0xE8:
        rel = struct.unpack_from('<i', code, p+1)[0]
        t = cur_va + 5 + rel
        print(f"0x{cur_va:08X}: call 0x{t:08X}")
        p += 5
        continue
    if b0 == 0xE9:
        rel = struct.unpack_from('<i', code, p+1)[0]
        t = cur_va + 5 + rel
        print(f"0x{cur_va:08X}: jmp 0x{t:08X}")
        p += 5
        continue
    if b0 == 0xEB:
        rel = struct.unpack_from('<b', code, p+1)[0]
        t = cur_va + 2 + rel
        print(f"0x{cur_va:08X}: jmp short 0x{t:08X}")
        p += 2
        continue
    if b0 == 0xC3:
        print(f"0x{cur_va:08X}: ret")
        p += 1
        continue
    if b0 == 0xC2:
        imm = struct.unpack_from('<H', code, p+1)[0]
        print(f"0x{cur_va:08X}: ret {imm}")
        p += 3
        continue
    # Let's print other interesting instructions or advance
    # Print cmp/test instructions
    if b0 in (0x39, 0x3B, 0x85, 0x83, 0x3C, 0x3D, 0x80, 0x81):
        # peek next few bytes
        h = ' '.join(f'{b:02x}' for b in code[p:min(p+7, len(code))])
        print(f"0x{cur_va:08X}: [TEST/CMP/OP] {h}")
    p += 1
