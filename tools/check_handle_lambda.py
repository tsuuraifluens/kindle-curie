with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

import struct

# In .data.rel.ro, around 0x3bbf0 - 0x3bc30
# Let's inspect all words around 0x3bc00 - 0x3bc30
off_start = 0x3bc00 - 0x10000
for i in range(0, 0x30, 4):
    va = 0x3bc00 + i
    val = struct.unpack("<I", elf_data[off_start + i : off_start + i + 4])[0]
    print(f"{hex(va)}: {hex(val)}")
