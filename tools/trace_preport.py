import struct

with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    data = f.read()

base = 0x01E00120

# Let's inspect each 16-bit word from 0xE580 to 0xE650
for pc in range(0xE580, 0xE650, 2):
    w = struct.unpack_from('<H', data, pc)[0]
    print(f'0x{pc:04X} (V: 0x{base+pc:08X}): {w:04X} ({w:016b})')
