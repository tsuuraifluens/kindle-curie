import struct

with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    data = f.read()
base = 0x01E00120

# Search for comparisons with handle numbers in 0x04310..0x045E0
for off in range(0x04310, 0x045E0, 2):
    w = struct.unpack_from('<H', data, off)[0]
    # In q32s: compare immediate e.g. cmp rX, #imm or sub rX, #imm
    # Check if imm matches handles: 0x03, 0x07, 0x0E, 0x1E, 0x21, 0x27, 0x48, 0x4D...
    for h in [0x03, 0x05, 0x07, 0x0E, 0x10, 0x12, 0x14, 0x16, 0x18, 0x1A, 0x1C, 0x1E, 0x21, 0x25, 0x27, 0x48, 0x4D]:
        # Check if h is in low byte or if w has h
        pass

# Let's inspect all strings referenced in read_callback
for off in range(0x04310, 0x045E0, 2):
    w0 = struct.unpack_from('<H', data, off)[0]
    if (w0 & 0xFFF0) == 0xFFC0:
        imm = struct.unpack_from('<I', data, off+2)[0]
        if base <= imm < base + len(data):
            s = data[imm-base:imm-base+40].split(b'\x00')[0]
            print(f'0x{off:05X}: {imm:08X} -> "{s.decode(errors="ignore")}"')
