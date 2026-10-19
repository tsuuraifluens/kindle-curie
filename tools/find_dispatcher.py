with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

import struct

needle = b"D def:dispatch:skip:dispatch_on mask 0x%02x excludes value=%d code=%u"
pos = elf_data.find(needle)
va = pos + 0x10000
print(f"String at VA {hex(va)}")

# Search for literal pointing to va
text_start = 0x18f50
text_end = 0x317f8
text_bytes = elf_data[text_start - 0x10000 : text_end - 0x10000]

for off in range(0, len(text_bytes) - 4, 4):
    val = struct.unpack("<i", text_bytes[off:off+4])[0]
    word_va = text_start + off
    if word_va + val == va:
        print(f"Literal at VA {hex(word_va)}: val={hex(val)}")
