import struct

with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    data = f.read()
base = 0x01E00120

# Let's decode q32s instructions accurately
def decode_q32s(data, start, count):
    pc = start
    for _ in range(count):
        if pc >= len(data): break
        vaddr = base + pc
        w0 = struct.unpack_from('<H', data, pc)[0]
        
        # 6-byte movi32
        if (w0 & 0xFFF0) == 0xFFC0:
            reg = w0 & 0x0F
            imm = struct.unpack_from('<I', data, pc+2)[0]
            comment = ''
            if base <= imm < base + len(data):
                s = data[imm-base:imm-base+32].split(b'\x00')[0]
                if len(s) >= 2: comment = f' ; "{s.decode(errors="ignore")}"'
            print(f'0x{pc:05X} (V: 0x{vaddr:08X}): mov r{reg}, 0x{imm:08X}{comment}')
            pc += 6
            continue
            
        # 4-byte call / jmp
        if (w0 & 0xFFC0) == 0xF380 or (w0 & 0xFFC0) == 0xF3C0:
            is_jmp = (w0 & 0xFFC0) == 0xF3C0
            w1 = struct.unpack_from('<H', data, pc+2)[0]
            imm6 = w0 & 0x3F
            if imm6 & 0x20: imm6 -= 0x40
            target = ((imm6 << 17) | (w1 << 1)) + vaddr + 4
            target_off = target - base
            name = 'jmp' if is_jmp else 'call'
            print(f'0x{pc:05X} (V: 0x{vaddr:08X}): {name} 0x{target:08X} (off 0x{target_off:05X})')
            pc += 4
            continue
            
        # 4-byte load/store: group=7, ins0412 in [0x100..0x17F]
        # Let's check 32-bit instructions in q32s
        group = (w0 >> 13) & 7
        if group == 7 and ((w0 >> 8) & 0x1F) in [0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D]:
            w1 = struct.unpack_from('<H', data, pc+2)[0]
            print(f'0x{pc:05X} (V: 0x{vaddr:08X}): [32b ins] {w0:04X} {w1:04X}')
            pc += 4
            continue
            
        # 2-byte instructions
        print(f'0x{pc:05X} (V: 0x{vaddr:08X}): {w0:04X}')
        pc += 2

print('=== Detailed Decode 0x0448C - 0x04510 ===')
decode_q32s(data, 0x0448C, 40)
