import struct

app = open('extracted/ufw_unpacked/files/app.bin', 'rb').read()
base = 0x01E00120

# Let's decode 0x4DD0 to 0x4E00 in full detail
# Let's inspect instructions around 0x4DE4:
for off in range(0x4DD8, 0x4DF8, 2):
    w = struct.unpack_from('<H', app, off)[0]
    print(f'0x{off:04X}: 0x{w:04X}')
