import struct

with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    data = f.read()

base = 0x01E00120

# Let's inspect the bytes around 0xE790 - 0xE7F0:
for off in range(0xE790, 0xE7F0, 2):
    w = struct.unpack_from('<H', data, off)[0]
    b0, b1 = data[off], data[off+1]
    print(f'0x{off:04X} (V:0x{base+off:08X}): {w:04X}  [{b0:02X} {b1:02X}]')
