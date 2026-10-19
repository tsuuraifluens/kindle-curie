import struct

app = open('extracted/ufw_unpacked/files/app.bin', 'rb').read()
base = 0x01E00120
target_off = 0x22A1C
target_vaddr = base + target_off

print(f"Finding all references to Table 3 (0x{target_off:05X} / VADDR 0x{target_vaddr:08X}):")

needle = struct.pack('<I', target_vaddr)
pos = 0
while True:
    pos = app.find(needle, pos)
    if pos == -1: break
    print(f"Direct pointer at offset 0x{pos:05X} (VADDR 0x{base+pos:08X})")
    pos += 1

for i in range(0, len(app)-6, 2):
    w0 = struct.unpack_from('<H', app, i)[0]
    if (w0 & 0xFFF0) == 0xFFC0:
        imm = struct.unpack_from('<I', app, i+2)[0]
        if imm == target_vaddr:
            print(f"mov rX, 0x{imm:08X} at offset 0x{i:05X}")
