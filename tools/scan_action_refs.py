with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

import struct

text_start = 0x18f50
text_end = 0x317f8
text_bytes = elf_data[text_start - 0x10000 : text_end - 0x10000]

print("Scanning for PC-relative or base-relative references to 0x33d00 - 0x33f00...")
for off in range(0, len(text_bytes) - 4, 2):
    val = struct.unpack("<i", text_bytes[off:off+4])[0]
    word_va = text_start + off
    for delta in [0, 2, 4, 8]:
        target = word_va + delta + val
        if 0x33db0 <= target <= 0x33ea0:
            # Read string at target
            t_off = target - 0x10000
            end = elf_data.find(b"\x00", t_off)
            s = elf_data[t_off:end]
            print(f"At VA {hex(word_va)} (delta {delta}, val={hex(val)}): target={hex(target)} -> {s}")
