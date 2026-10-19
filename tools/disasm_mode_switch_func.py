import struct

with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    app = f.read()

base_addr = 0x01E00120
off = 0x07260
end = 0x07480

while off < end:
    vaddr = base_addr + off
    w0 = struct.unpack_from('<H', app, off)[0]
    
    # 6-byte mov imm32
    if (w0 & 0xFFF0) == 0xFFC0:
        reg = w0 & 0xF
        imm = struct.unpack_from('<I', app, off+2)[0]
        s = ''
        if base_addr <= imm < base_addr + len(app):
            st = app[imm-base_addr:imm-base_addr+30].split(b'\x00')[0]
            if len(st) >= 2:
                s = f' ; "{st.decode(errors="ignore")}"'
        print(f'0x{off:04X}: mov r{reg}, 0x{imm:08X}{s}')
        off += 6
        continue
        
    # call
    if (w0 & 0xFFC0) == 0xF380:
        w1 = struct.unpack_from('<H', app, off+2)[0]
        imm6 = w0 & 0x3F
        if imm6 & 0x20: imm6 -= 0x40
        target = ((imm6 << 17) | (w1 << 1)) + vaddr + 4
        target_off = target - base_addr
        print(f'0x{off:04X}: call 0x{target:08X} (off 0x{target_off:05X})')
        off += 4
        continue

    # group 1: jz / jnz
    grp = (w0 >> 13) & 7
    if grp == 1:
        ins12 = (w0 >> 12) & 1
        regDl = (w0 >> 9) & 7
        imm9 = w0 & 0x1FF
        if imm9 & 0x100:
            imm9 -= 0x200
        target = vaddr + 2 + (imm9 << 1)
        target_off = target - base_addr
        name = 'jnz' if ins12 else 'jz'
        print(f'0x{off:04X}: {name} r{regDl}, 0x{target_off:04X}')
        off += 2
        continue

    # goto
    if (w0 & 0xF800) == 0x1800:
        imm11 = w0 & 0x7FF
        if imm11 & 0x400:
            imm11 -= 0x800
        target = off + 2 + (imm11 << 1)
        print(f'0x{off:04X}: goto 0x{target:04X}')
        off += 2
        continue

    # group 7 / other
    if (w0 >> 12) == 0x7:
        reg = w0 & 0xF
        imm = (w0 >> 4) & 0xFF
        print(f'0x{off:04X}: mov r{reg}, #0x{imm:02X}')
        off += 2
        continue

    print(f'0x{off:04X}: 0x{w0:04X}')
    off += 2
