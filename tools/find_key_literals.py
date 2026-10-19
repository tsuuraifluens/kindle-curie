with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

import struct

text_start = 0x18f50
text_end = 0x317f8
text_bytes = elf_data[text_start - 0x10000 : text_end - 0x10000]

print("Searching for relative offsets pointing into 0x34a00 - 0x34d00...")
for off in range(0, len(text_bytes) - 4, 4):
    val = struct.unpack("<i", text_bytes[off:off+4])[0]
    word_va = text_start + off
    target = word_va + val
    if 0x34a00 <= target <= 0x34d00:
        str_off = target - 0x10000
        end = elf_data.find(b"\x00", str_off)
        s = elf_data[str_off:end]
        print(f"Literal at {hex(word_va)}: val={hex(val)} -> target {hex(target)}: {s}")
