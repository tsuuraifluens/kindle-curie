with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

import struct

# In input_manager:
# Linux evdev code for KEY_WAKEUP is 143 (0x8F)!
# Linux evdev code for KEY_SLEEP is 142 (0x8E)!
# Linux evdev code for KEY_POWER is 116 (0x74)!
# Linux evdev code for KEY_PAGEDOWN is 109 (0x6D)!
# Linux evdev code for KEY_PAGEUP is 104 (0x68)!
# Linux evdev code for KEY_BRIGHTNESSUP is 225 (0xE1)!
# Linux evdev code for KEY_BRIGHTNESSDOWN is 224 (0xE0)!

# Search for 143 (0x8f) in .text or .rodata
print("Searching for evdev keycode 143 (KEY_WAKEUP) and 116 (KEY_POWER)...")
for code, name in [(143, "KEY_WAKEUP (143)"), (142, "KEY_SLEEP (142)"), (116, "KEY_POWER (116)")]:
    pat = struct.pack("<H", code) # 16-bit
    pos = 0
    while True:
        pos = elf_data.find(pat, pos)
        if pos == -1:
            break
        va = pos + 0x10000
        # only report if in text or rodata
        if 0x18f50 <= va <= 0x36870:
            # check 32-bit alignment
            if pos % 4 == 0:
                val32 = struct.unpack("<I", elf_data[pos:pos+4])[0]
                if val32 == code:
                    print(f"32-bit word {name} at VA {hex(va)}")
        pos += 2
