import struct

app = open('extracted/ufw_unpacked/files/app.bin', 'rb').read()
base = 0x01E00120
target_off = 0x74C6
target_vaddr = base + target_off

print(f'Searching references to target offset 0x{target_off:04X} (VADDR 0x{target_vaddr:08X}):')

# 1. Search pointer in data/rodata
for i in range(0, len(app)-4, 2):
    val = struct.unpack_from('<I', app, i)[0]
    if val == target_vaddr or val == target_vaddr + 1:
        print(f'Pointer at offset 0x{i:05X} (VADDR 0x{base+i:08X}): 0x{val:08X}')

# 2. Search call instructions
for off in range(0, len(app)-4, 2):
    w0 = struct.unpack_from('<H', app, off)[0]
    if (w0 & 0xFFC0) == 0xF380:
        w1 = struct.unpack_from('<H', app, off+2)[0]
        imm6 = w0 & 0x3F
        if imm6 & 0x20: imm6 -= 0x40
        dest = ((imm6 << 17) | (w1 << 1)) + base + off + 4
        if dest == target_vaddr:
            print(f'Call from offset 0x{off:05X} (VADDR 0x{base+off:08X})')
