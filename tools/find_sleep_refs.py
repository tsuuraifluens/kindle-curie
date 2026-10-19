with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

import struct

targets = {
    0x33de6: "system.sleep",
    0x33e34: "D def:action:sleep:device=%s rc=%d",
    0x33db8: "display.brightness_up",
    0x33dce: "display.brightness_down",
}

text_start = 0x18f50
text_end = 0x317f8
text_bytes = elf_data[text_start - 0x10000 : text_end - 0x10000]

print("Searching for relative offsets to all targets...")
for off in range(0, len(text_bytes) - 4, 2):
    val = struct.unpack("<i", text_bytes[off:off+4])[0]
    word_va = text_start + off
    # Check if word_va + delta + val == target
    for delta in range(-16, 20, 2):
        for t_addr, t_name in targets.items():
            if (word_va + delta + val) == t_addr:
                print(f"Match at {hex(word_va)} (delta {delta}): val={hex(val)} -> {t_name} ({hex(t_addr)})")
