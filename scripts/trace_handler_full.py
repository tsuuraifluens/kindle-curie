import sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off
import struct

start_va = 0x004BCA80
# Find end of function
# Look for ret (0xC3 or 0xC2)
start_off = va_to_off(start_va)

# Let's inspect from start_va to start_va + 0x1200
code = data[start_off : start_off + 0x1500]

# Simple x86 disassembler or instruction walker
p = 0
while p < len(code) - 10:
    va = start_va + p
    b0 = code[p]
    b1 = code[p+1]
    
    # Check for calls
    if b0 == 0xE8:
        rel = struct.unpack_from('<i', code, p+1)[0]
        t = va + 5 + rel
        t_off = va_to_off(t)
        name = ""
        # Check if t has known name or strings
        print(f"0x{va:08X}: call 0x{t:08X} {name}")
        p += 5
        continue
    # Check for jumps
    if b0 == 0xE9:
        rel = struct.unpack_from('<i', code, p+1)[0]
        t = va + 5 + rel
        print(f"0x{va:08X}: jmp 0x{t:08X}")
        p += 5
        continue
    if b0 == 0xEB:
        rel = struct.unpack_from('<b', code, p+1)[0]
        t = va + 2 + rel
        print(f"0x{va:08X}: jmp short 0x{t:08X}")
        p += 2
        continue
    if b0 == 0x0F and 0x80 <= b1 <= 0x8F:
        rel = struct.unpack_from('<i', code, p+2)[0]
        cond = ['jo','jno','jb','jnb','je','jne','jbe','ja','js','jns','jp','jnp','jl','jge','jle','jg'][b1 - 0x80]
        t = va + 6 + rel
        print(f"0x{va:08X}: {cond} 0x{t:08X}")
        p += 6
        continue
    if 0x70 <= b0 <= 0x7F:
        rel = struct.unpack_from('<b', code, p+1)[0]
        cond = ['jo','jno','jb','jnb','je','jne','jbe','ja','js','jns','jp','jnp','jl','jge','jle','jg'][b0 - 0x70]
        t = va + 2 + rel
        print(f"0x{va:08X}: {cond} 0x{t:08X}")
        p += 2
        continue
    if b0 in (0xC3, 0xC2):
        if b0 == 0xC3:
            print(f"0x{va:08X}: ret")
            p += 1
        else:
            imm = struct.unpack_from('<H', code, p+1)[0]
            print(f"0x{va:08X}: ret {imm}")
            p += 3
        # If we see ret and next is 0xCC or 0x90 or next function header, let's note it
        if code[p] in (0xCC, 0x90, 0x55):
            print(f"--- Potential end of func at 0x{va:08X} ---")
        continue
    p += 1
