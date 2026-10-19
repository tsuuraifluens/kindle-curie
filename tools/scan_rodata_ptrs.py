with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

import struct

text_start = 0x18f50
text_end = 0x317f8
text_bytes = elf_data[text_start - 0x10000 : text_end - 0x10000]

# Check direct pointers first in entire binary
print("Scanning entire ELF for pointers into .rodata (0x31800 - 0x36870):")
rodata_ptrs = []
for off in range(0, len(elf_data) - 4, 4):
    val = struct.unpack("<I", elf_data[off:off+4])[0]
    if 0x33d00 <= val <= 0x34000:
        va = off + 0x10000
        # What string is at val?
        str_off = val - 0x10000
        end_str = elf_data.find(b"\x00", str_off)
        s = elf_data[str_off:end_str]
        print(f"Found pointer at VA {hex(va)} -> {hex(val)}: {s}")
