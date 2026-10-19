with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

import struct
import capstone

# Find all occurrences of the RTTI addresses:
# 0x3bce8, 0x3bcf0, 0x3bcf8 or the rodata strings 0x34043, 0x340bf, 0x3413c
targets = [0x34043, 0x340bf, 0x3413c]
for t in targets:
    pat = struct.pack("<I", t)
    pos = 0
    while True:
        pos = elf_data.find(pat, pos)
        if pos == -1:
            break
        print(f"RTTI string {hex(t)} referenced at VA {hex(pos + 0x10000)}")
        pos += 4

# Check surrounding area in .data.rel.ro around 0x3bce0 - 0x3bd10
print("\n--- .data.rel.ro around 0x3bce0 - 0x3bd10 ---")
off_start = 0x3bce0 - 0x10000
for i in range(0, 0x40, 4):
    va = 0x3bce0 + i
    val = struct.unpack("<I", elf_data[off_start + i : off_start + i + 4])[0]
    print(f"  {hex(va)}: {hex(val)}")
