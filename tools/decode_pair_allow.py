import struct

with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    data = f.read()
base = 0x01E00120

def decode_insns(start, count=80):
    pc = start
    end = start + count * 4
    for _ in range(count):
        if pc >= len(data): break
        vaddr = base + pc
        w0 = struct.unpack_from('<H', data, pc)[0]
        
        # 6-byte mov imm32: 1111 1111 1100 rrrr
        if (w0 & 0xFFF0) == 0xFFC0:
            reg = w0 & 0x0F
            imm = struct.unpack_from('<I', data, pc+2)[0]
            comment = ''
            if base <= imm < base + len(data):
                str_cand = data[imm-base:imm-base+40].split(b'\x00')[0]
                if len(str_cand) >= 2:
                    comment = f' ; "{str_cand.decode(errors="ignore")}"'
            print(f'0x{pc:05X} (V: 0x{vaddr:08X}): mov r{reg}, 0x{imm:08X}{comment}')
            pc += 6
            continue
            
        # 4-byte call: 1111 0011 10xx xxxx
        if (w0 & 0xFFC0) == 0xF380:
            w1 = struct.unpack_from('<H', data, pc+2)[0]
            imm6 = w0 & 0x3F
            if imm6 & 0x20: imm6 -= 0x40
            target = ((imm6 << 17) | (w1 << 1)) + vaddr + 4
            target_off = target - base
            print(f'0x{pc:05X} (V: 0x{vaddr:08X}): call 0x{target:08X} (off 0x{target_off:05X})')
            pc += 4
            continue
            
        # 4-byte jmp: 1111 0011 11xx xxxx
        if (w0 & 0xFFC0) == 0xF3C0:
            w1 = struct.unpack_from('<H', data, pc+2)[0]
            imm6 = w0 & 0x3F
            if imm6 & 0x20: imm6 -= 0x40
            target = ((imm6 << 17) | (w1 << 1)) + vaddr + 4
            target_off = target - base
            print(f'0x{pc:05X} (V: 0x{vaddr:08X}): jmp 0x{target:08X} (off 0x{target_off:05X})')
            pc += 4
            continue
            
        # 2-byte branch conditional
        # In q32s, conditional branches:
        # e.g., if (cond) jmp / if (cond) rts
        # Let's inspect raw hex
        print(f'0x{pc:05X} (V: 0x{vaddr:08X}): {w0:04X} ({w0 & 0xFF:02x} {w0 >> 8:02x})')
        pc += 2

print('=== Decode 0x18720 - 0x187B0 ===')
decode_insns(0x18720, 60)
