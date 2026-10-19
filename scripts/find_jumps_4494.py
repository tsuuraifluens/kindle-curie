import struct

app = open('extracted/ufw_unpacked/files/app.bin', 'rb').read()
base = 0x01E00120
target_off = 0x4494

for off in range(0x4000, 0x4800, 2):
    w0 = struct.unpack_from('<H', app, off)[0]
    grp = (w0 >> 13) & 7
    if (w0 & 0xF800) == 0x1800:
        imm11 = w0 & 0x7FF
        if imm11 & 0x400: imm11 -= 0x800
        dest = off + 2 + (imm11 << 1)
        if dest == target_off:
            print(f'goto from 0x{off:04X} to 0x{target_off:04X}')
    if grp == 7 and ((w0 >> 12) & 1) == 0:
        w1 = struct.unpack_from('<H', app, off+2)[0]
        pcrel = w1 & 0xFFF
        if pcrel & 0x800: pcrel -= 0x1000
        dest = off + 4 + (pcrel << 1)
        if dest == target_off:
            cond = (w1 >> 12) & 0xF
            print(f'branch cond {cond} from 0x{off:04X} to 0x{target_off:04X}')
