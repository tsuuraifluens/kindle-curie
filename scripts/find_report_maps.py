import struct

app = open('extracted/ufw_unpacked/files/app.bin', 'rb').read()
base = 0x01E00120

def find_map_refs(off, name):
    target = base + off
    print(f"\nReferences to {name} at offset 0x{off:05X} (VADDR 0x{target:08X}):")
    needle = struct.pack('<I', target)
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
            if imm == target:
                reg = w0 & 0xF
                print(f"mov r{reg}, 0x{imm:08X} at offset 0x{i:05X}")

find_map_refs(0x2280C, "Consumer Report Map (58B)")
find_map_refs(0x2260A, "Keyboard Report Map (64B)")
find_map_refs(0x2248C, "X-Input Report Map")
find_map_refs(0x2264F, "Mouse Report Map")
find_map_refs(0x226AA, "Touch Report Map")
