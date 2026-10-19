import struct

app = open('extracted/ufw_unpacked/files/app.bin', 'rb').read()
base = 0x01E00120

# Let's inspect all instructions from 0x74A0 to 0x74E6
# We'll print the hex bytes of each instruction and its decoded form
off = 0x74A8
while off < 0x74E6:
    w0 = struct.unpack_from('<H', app, off)[0]
    grp = (w0 >> 13) & 7
    if (w0 & 0xFFF0) == 0xFFC0:
        reg = w0 & 0xF
        imm = struct.unpack_from('<I', app, off+2)[0]
        print(f'0x{off:04X}: mov r{reg}, 0x{imm:08X}')
        off += 6
        continue
    if (w0 & 0xFFC0) == 0xF380:
        w1 = struct.unpack_from('<H', app, off+2)[0]
        imm6 = w0 & 0x3F
        if imm6 & 0x20: imm6 -= 0x40
        target = ((imm6 << 17) | (w1 << 1)) + base + off + 4
        print(f'0x{off:04X}: call 0x{target:08X} (off 0x{target-base:05X})')
        off += 4
        continue
    if grp == 7 and ((w0 >> 12) & 1) == 0:
        w1 = struct.unpack_from('<H', app, off+2)[0]
        cond = (w1 >> 12) & 0xF
        pcrel = w1 & 0xFFF
        if pcrel & 0x800: pcrel -= 0x1000
        target = base + off + 4 + (pcrel << 1)
        print(f'0x{off:04X}: branch cond {cond} to 0x{target-base:04X}')
        off += 4
        continue
    if (w0 & 0xF800) == 0x1800:
        imm11 = w0 & 0x7FF
        if imm11 & 0x400: imm11 -= 0x800
        target = base + off + 2 + (imm11 << 1)
        print(f'0x{off:04X}: goto 0x{target-base:04X}')
        off += 2
        continue
    print(f'0x{off:04X}: 0x{w0:04X} ({app[off]:02X} {app[off+1]:02X})')
    off += 2
