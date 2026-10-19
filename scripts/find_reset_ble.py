import struct

app = open('extracted/ufw_unpacked/files/app.bin', 'rb').read()
base = 0x01E00120

needle = b'###reset ble list'
pos = app.find(needle)
print(f"String offset: 0x{pos:05X} (VADDR 0x{base+pos:08X})")

target = base + pos
for i in range(0, len(app)-6, 2):
    w0 = struct.unpack_from('<H', app, i)[0]
    if (w0 & 0xFFF0) == 0xFFC0:
        imm = struct.unpack_from('<I', app, i+2)[0]
        if imm == target:
            print(f"Referenced at offset 0x{i:05X}")
