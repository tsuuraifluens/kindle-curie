import struct

app = open('extracted/ufw_unpacked/files/app.bin', 'rb').read()

for off in range(0x4300, 0x4480, 2):
    w0 = struct.unpack_from('<H', app, off)[0]
    if (w0 & 0xFFF0) == 0x00A0 or (w0 & 0xFFF0) == 0x00B0:
        print(f'tbb/tbh at offset 0x{off:04X}: 0x{w0:04X}')
