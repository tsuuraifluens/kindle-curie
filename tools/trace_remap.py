import struct

with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    data = f.read()

base = 0x01E00120

# Let's inspect 0xE760 to 0xE7BC in detail
for pc in range(0xE760, 0xE7C0, 2):
    w0 = struct.unpack_from('<H', data, pc)[0]
    print(f'0x{pc:04X} (V: 0x{base+pc:08X}): {w0:04X} ({w0:016b})')
