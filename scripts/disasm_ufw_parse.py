import struct, sys
sys.path.insert(0, 'scripts')
from re_meta import data, off_to_va, va_to_off

start_va = 0x00465AF0
end_va = 0x00466020
start_off = va_to_off(start_va)
end_off = va_to_off(end_va)

# Let's write a targeted disassembler for x86 in this function
def disasm_block(start_va, end_va):
    cur_off = va_to_off(start_va)
    end_off = va_to_off(end_va)
    
    pos = cur_off
    while pos < end_off:
        va = off_to_va(pos)
        b = data[pos]
        
        # Check some common patterns
        if b == 0xE8: # call
            rel = struct.unpack_from('<i', data, pos + 1)[0]
            target = va + 5 + rel
            print(f"0x{va:08X}: call 0x{target:08X}")
            pos += 5
        elif b == 0xE9: # jmp rel32
            rel = struct.unpack_from('<i', data, pos + 1)[0]
            target = va + 5 + rel
            print(f"0x{va:08X}: jmp 0x{target:08X}")
            pos += 5
        elif b == 0xEB: # jmp rel8
            rel = struct.unpack_from('<b', data, pos + 1)[0]
            target = va + 2 + rel
            print(f"0x{va:08X}: jmp short 0x{target:08X}")
            pos += 2
        elif b == 0x0F and data[pos+1] in range(0x80, 0x90): # jcc rel32
            cond = ['jo', 'jno', 'jb', 'jnb', 'je', 'jne', 'jbe', 'ja', 'js', 'jns', 'jp', 'jnp', 'jl', 'jge', 'jle', 'jg'][data[pos+1] - 0x80]
            rel = struct.unpack_from('<i', data, pos + 2)[0]
            target = va + 6 + rel
            print(f"0x{va:08X}: {cond} 0x{target:08X}")
            pos += 6
        elif b in range(0x70, 0x80): # jcc rel8
            cond = ['jo', 'jno', 'jb', 'jnb', 'je', 'jne', 'jbe', 'ja', 'js', 'jns', 'jp', 'jnp', 'jl', 'jge', 'jle', 'jg'][b - 0x70]
            rel = struct.unpack_from('<b', data, pos + 1)[0]
            target = va + 2 + rel
            print(f"0x{va:08X}: {cond} short 0x{target:08X}")
            pos += 2
        elif b == 0xC3 or b == 0xC2: # ret
            if b == 0xC2:
                imm16 = struct.unpack_from('<H', data, pos + 1)[0]
                print(f"0x{va:08X}: ret {imm16}")
                pos += 3
            else:
                print(f"0x{va:08X}: ret")
                pos += 1
        elif b in (0x68,): # push imm32
            imm32 = struct.unpack_from('<I', data, pos + 1)[0]
            s_off = va_to_off(imm32)
            s_str = ""
            if s_off and 0 <= s_off < len(data) - 4:
                s = data[s_off:s_off+30].split(b'\x00')[0]
                if len(s) >= 2 and all(0x20 <= c <= 0x7E for c in s):
                    s_str = f" -> '{s.decode('latin1')}'"
            print(f"0x{va:08X}: push 0x{imm32:08X}{s_str}")
            pos += 5
        elif b == 0x6A: # push imm8
            imm8 = struct.unpack_from('<B', data, pos + 1)[0]
            print(f"0x{va:08X}: push 0x{imm8:02X}")
            pos += 2
        else:
            # print single byte or move forward
            pos += 1

disasm_block(start_va, end_va)
