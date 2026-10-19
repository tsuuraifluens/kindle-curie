import struct

app = open('extracted/ufw_unpacked/files/app.bin', 'rb').read()
base = 0x01E00120

off = 0x74B0
while off < 0x7520:
    w0 = struct.unpack_from('<H', app, off)[0]
    vaddr = base + off
    if (w0 & 0xFFF0) == 0xFFC0:
        reg = w0 & 0xF
        imm = struct.unpack_from('<I', app, off+2)[0]
        s = ''
        if base <= imm < base + len(app):
            st = app[imm-base:imm-base+30].split(b'\x00')[0]
            if len(st) >= 2: s = f' ; "{st.decode(errors="ignore")}"'
        print(f'0x{off:04X}: mov r{reg}, 0x{imm:08X}{s}')
        off += 6
        continue
    if (w0 & 0xFFC0) == 0xF380:
        w1 = struct.unpack_from('<H', app, off+2)[0]
        imm6 = w0 & 0x3F
        if imm6 & 0x20: imm6 -= 0x40
        target = ((imm6 << 17) | (w1 << 1)) + vaddr + 4
        print(f'0x{off:04X}: call 0x{target:08X} (off 0x{target-base:05X})')
        off += 4
        continue
    if (w0 >> 13) == 7 and ((w0 >> 12) & 1) == 0:
        w1 = struct.unpack_from('<H', app, off+2)[0]
        cond = (w1 >> 12) & 0xF
        pcrel = w1 & 0xFFF
        if pcrel & 0x800: pcrel -= 0x1000
        target = vaddr + 4 + (pcrel << 1)
        print(f'0x{off:04X}: branch cond {cond} to 0x{target:08X} (off 0x{target-base:04X})')
        off += 4
        continue
    if (w0 & 0xF800) == 0x1800:
        imm11 = w0 & 0x7FF
        if imm11 & 0x400: imm11 -= 0x800
        target = vaddr + 2 + (imm11 << 1)
        print(f'0x{off:04X}: goto 0x{target:08X} (off 0x{target-base:04X})')
        off += 2
        continue
    print(f'0x{off:04X}: 0x{w0:04X}')
    off += 2
