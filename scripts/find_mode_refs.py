import struct

app = open('extracted/ufw_unpacked/files/app.bin', 'rb').read()
base = 0x01E00120

def find_refs(target_addr, name):
    print(f"\nFinding all references to 0x{target_addr:08X} ({name}):")
    needle = struct.pack('<I', target_addr)
    pos = 0
    while True:
        pos = app.find(needle, pos)
        if pos == -1: break
        print(f"Direct pointer at offset 0x{pos:05X} (VADDR 0x{base+pos:08X})")
        pos += 1
    for off in range(0, len(app)-6, 2):
        w0 = struct.unpack_from('<H', app, off)[0]
        if (w0 & 0xFFF0) == 0xFFC0:
            imm = struct.unpack_from('<I', app, off+2)[0]
            if imm == target_addr:
                reg = w0 & 0xF
                print(f"mov r{reg}, 0x{imm:08X} at offset 0x{off:05X} (VADDR 0x{base+off:08X})")

find_refs(0x01E212E6, "IINE_reader")
find_refs(0x01E2130B, "IINE_keyboard")
find_refs(0x01E212F2, "Xbox Wireless Controller")
find_refs(0x01E21319, "IINE_mouse")
find_refs(0x01E21324, "IINE_phone")
find_refs(0x01E2132F, "IINE_control")
