import struct

with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    data = f.read()

base = 0x01E00120

def decode_range(start, end):
    pc = start
    while pc < end:
        vaddr = base + pc
        w0 = struct.unpack_from('<H', data, pc)[0]
        if (w0 & 0xFFF0) == 0xFFC0:
            reg = w0 & 0x0F
            imm = struct.unpack_from('<I', data, pc+2)[0]
            comment = ''
            if base <= imm < base + len(data):
                str_cand = data[imm-base:imm-base+40].split(b'\x00')[0]
                if len(str_cand) >= 2:
                    comment = f' ; "{str_cand.decode(errors="ignore")}"'
            print(f'0x{pc:04X} (V: 0x{vaddr:08X}): mov r{reg}, 0x{imm:08X}{comment}')
            pc += 6
            continue
        if (w0 & 0xFFC0) == 0xF380:
            w1 = struct.unpack_from('<H', data, pc+2)[0]
            imm6 = w0 & 0x3F
            if imm6 & 0x20:
                imm6 -= 0x40
            target = ((imm6 << 17) | (w1 << 1)) + vaddr + 4
            target_off = target - base
            print(f'0x{pc:04X} (V: 0x{vaddr:08X}): call 0x{target:08X} (off 0x{target_off:05X})')
            pc += 4
            continue
        print(f'0x{pc:04X} (V: 0x{vaddr:08X}): {w0:04X}')
        pc += 2

print('=== Range 0x4030 - 0x4090 ===')
decode_range(0x4030, 0x4090)
