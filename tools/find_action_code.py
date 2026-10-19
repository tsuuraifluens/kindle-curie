with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

import struct

targets = [
    (0x33e34, "D def:action:sleep..."),
    (0x33e57, "D def:action:brightness_down..."),
    (0x33e84, "D def:action:brightness_up..."),
    (0x33de6, "system.sleep"),
    (0x33db8, "display.brightness_up"),
    (0x33dce, "display.brightness_down"),
]

for va, name in targets:
    # search as direct 32-bit pointer
    pat = struct.pack("<I", va)
    pos = 0
    while True:
        pos = elf_data.find(pat, pos)
        if pos == -1:
            break
        print(f"Direct pointer to '{name}' ({hex(va)}) at VA {hex(pos + 0x10000)}")
        pos += 4
