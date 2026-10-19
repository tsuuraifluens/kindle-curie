with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

import struct

targets = [0x3bce8, 0x3bcf0, 0x3bcf8]
for t in targets:
    pat = struct.pack("<I", t)
    pos = 0
    while True:
        pos = elf_data.find(pat, pos)
        if pos == -1:
            break
        print(f"Typeinfo {hex(t)} referenced at VA {hex(pos + 0x10000)}")
        pos += 4
