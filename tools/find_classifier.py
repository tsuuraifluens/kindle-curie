with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

import struct

# Search for offset of b": unresolvable required_keys entry '"
needle = b": unresolvable required_keys entry '"
pos = elf_data.find(needle)
va = pos + 0x10000
print(f"String at VA {hex(va)}")

# Search for references in .text
text_start = 0x18f50
text_end = 0x317f8
text_bytes = elf_data[text_start - 0x10000 : text_end - 0x10000]

for off in range(0, len(text_bytes) - 4, 2):
    val = struct.unpack("<i", text_bytes[off:off+4])[0]
    word_va = text_start + off
    for delta in [0, 2, 4, 8]:
        if word_va + delta + val == va:
            print(f"Reference at VA {hex(word_va)} (delta {delta})")
