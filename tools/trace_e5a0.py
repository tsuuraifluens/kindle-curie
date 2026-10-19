import struct

with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    data = f.read()

base = 0x01E00120

# Let's inspect instructions from 0xE5A0 to 0xE5E0
for pc in range(0xE5A0, 0xE5E0, 2):
    w0 = struct.unpack_from('<H', data, pc)[0]
    group = (w0 >> 13) & 7
    ins0412 = (w0 >> 4) & 0x1FF
    ins0812 = (w0 >> 8) & 0x1F
    regA = w0 & 0xF
    regB = (w0 >> 4) & 0xF
    # Print decoded summary
    print(f'0x{pc:04X} (V:0x{base+pc:08X}): 0x{w0:04X} (grp={group}, 0412=0x{ins0412:X}, 0812=0x{ins0812:X}, rA={regA}, rB={regB})')
