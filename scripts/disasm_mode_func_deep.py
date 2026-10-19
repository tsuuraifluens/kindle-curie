import struct

with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    app = f.read()

base_addr = 0x01E00120

def sext(val, bits):
    if val & (1 << (bits - 1)):
        return val - (1 << bits)
    return val

def disassemble_range(start, end):
    pc = start
    while pc < end:
        vaddr = base_addr + pc
        w0 = struct.unpack_from('<H', app, pc)[0]
        
        # 6-byte mov imm32: 0xFFC0..0xFFCF
        if (w0 & 0xFFF0) == 0xFFC0:
            reg = w0 & 0xF
            imm = struct.unpack_from('<I', app, pc+2)[0]
            hint = ''
            if base_addr <= imm < base_addr + len(app):
                s = app[imm-base_addr:imm-base_addr+32].split(b'\x00')[0]
                if len(s) >= 2:
                    hint = f' ; "{s.decode(errors="ignore")}"'
            print(f'0x{pc:04X} (V:0x{vaddr:08X}): mov r{reg}, 0x{imm:08X}{hint}')
            pc += 6
            continue
            
        # 4-byte call pcrel23m2
        if (w0 & 0xFFC0) == 0xF380:
            addr0005s = sext(w0 & 0x3F, 6)
            w1 = struct.unpack_from('<H', app, pc+2)[0]
            target = ((addr0005s << 17) | (w1 << 1)) + (vaddr + 4)
            print(f'0x{pc:04X} (V:0x{vaddr:08X}): call 0x{target:08X} (off 0x{target-base_addr:05X})')
            pc += 4
            continue
            
        # 4-byte goto pcrel23m2
        if (w0 & 0xFFC0) == 0xF3C0:
            addr0005s = sext(w0 & 0x3F, 6)
            w1 = struct.unpack_from('<H', app, pc+2)[0]
            target = ((addr0005s << 17) | (w1 << 1)) + (vaddr + 4)
            print(f'0x{pc:04X} (V:0x{vaddr:08X}): goto 0x{target:08X} (off 0x{target-base_addr:05X})')
            pc += 4
            continue
            
        # 4-byte jbc
        if (w0 & 0xFFE0) == 0xF300:
            bit = w0 & 0x1F
            w1 = struct.unpack_from('<H', app, pc+2)[0]
            reg = (w1 >> 12) & 0xF
            disp = sext(w1 & 0xFFF, 12)
            target = (disp << 1) + (vaddr + 4)
            print(f'0x{pc:04X} (V:0x{vaddr:08X}): jbc r{reg}:{bit}, 0x{target:08X} (off 0x{target-base_addr:05X})')
            pc += 4
            continue
            
        # 4-byte jbs
        if (w0 & 0xFFE0) == 0xF320:
            bit = w0 & 0x1F
            w1 = struct.unpack_from('<H', app, pc+2)[0]
            reg = (w1 >> 12) & 0xF
            disp = sext(w1 & 0xFFF, 12)
            target = (disp << 1) + (vaddr + 4)
            print(f'0x{pc:04X} (V:0x{vaddr:08X}): jbs r{reg}:{bit}, 0x{target:08X} (off 0x{target-base_addr:05X})')
            pc += 4
            continue

        # 4-byte load/store: group=7, 0xF900..0xF9FF
        if (w0 & 0xFF00) == 0xF900:
            w1 = struct.unpack_from('<H', app, pc+2)[0]
            # e.g. lb.u regA, [regE + addr1627s]
            # ins0412=0x190: 0xF900 | regA
            regA = w0 & 0xF
            regE = (w1 >> 12) & 0xF
            disp = sext(w1 & 0xFFF, 12)
            print(f'0x{pc:04X} (V:0x{vaddr:08X}): lb.u r{regA}, [r{regE} + 0x{disp:X}] (0x{w0:04X} 0x{w1:04X})')
            pc += 4
            continue

        # 2-byte goto pcrel12m2: 0x1800..0x1FFF
        if (w0 & 0xF800) == 0x1800:
            disp = sext(w0 & 0x7FF, 11)
            target = (disp << 1) + (vaddr + 2)
            print(f'0x{pc:04X} (V:0x{vaddr:08X}): goto 0x{target:08X} (off 0x{target-base_addr:05X})')
            pc += 2
            continue
            
        # 2-byte call pcrel12m2: 0x1000..0x17FF
        if (w0 & 0xF800) == 0x1000:
            disp = sext(w0 & 0x7FF, 11)
            target = (disp << 1) + (vaddr + 2)
            print(f'0x{pc:04X} (V:0x{vaddr:08X}): call 0x{target:08X} (off 0x{target-base_addr:05X})')
            pc += 2
            continue
            
        # 2-byte mov rA, #imm8: 0x7000..0x7FFF
        if (w0 & 0xF000) == 0x7000:
            reg = w0 & 0xF
            imm = (w0 >> 4) & 0xFF
            print(f'0x{pc:04X} (V:0x{vaddr:08X}): mov r{reg}, #{imm} (0x{imm:02X})')
            pc += 2
            continue
            
        # 2-byte mov rA, rB: 0x0800..0x08FF
        if (w0 & 0xFF00) == 0x0800:
            regA = w0 & 0xF
            regB = (w0 >> 4) & 0xF
            print(f'0x{pc:04X} (V:0x{vaddr:08X}): mov r{regA}, r{regB}')
            pc += 2
            continue
            
        # 2-byte add regA, sp, #imm: 0xA000..0xAFFF
        if (w0 & 0xF000) == 0xA000:
            regA = w0 & 0xF
            imm = (w0 >> 4) & 0xFF
            print(f'0x{pc:04X} (V:0x{vaddr:08X}): add r{regA}, sp, #{imm} (0x{imm:02X})')
            pc += 2
            continue

        print(f'0x{pc:04X} (V:0x{vaddr:08X}): raw 0x{w0:04X}')
        pc += 2

print("=== Disassembly of Mode Init / Dispatch (0x7080 - 0x7200) ===")
disassemble_range(0x7080, 0x7200)
