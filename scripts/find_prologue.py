import struct

app = open('extracted/ufw_unpacked/files/app.bin', 'rb').read()

# Let's search backwards from 0x74A0 for function prologue (e.g. add sp, -size or push/rets)
for off in range(0x7380, 0x7480, 2):
    w0 = struct.unpack_from('<H', app, off)[0]
    # Check add sp, -imm or similar
    if (w0 & 0xFFF0) == 0xFBC0 or (w0 & 0xFF00) == 0xBC00:
        print(f'Possible prologue at 0x{off:04X}: 0x{w0:04X}')
