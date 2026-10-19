import struct

app = open('extracted/ufw_unpacked/files/app.bin', 'rb').read()
base = 0x01E00120

# Search where 0x1E is checked (e.g. jeq reg, #0x1E or similar)
for off in range(0x4000, 0x4800, 2):
    w0 = struct.unpack_from('<H', app, off)[0]
    grp = (w0 >> 13) & 7
    if grp == 7 and ((w0 >> 12) & 1) == 0:
        w1 = struct.unpack_from('<H', app, off+2)[0]
        imm = (w0 >> 4) & 0xFF
        if imm == 0x1E or imm == 30:
            print(f'Branch on 0x1E at offset 0x{off:04X}: w0=0x{w0:04X} w1=0x{w1:04X}')
