with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

import struct

# Find all pointers to KEY_POWER (0x34b8c), KEY_WAKEUP (0x34c6c), KEY_SLEEP (0x34c62), KEY_PAGEDOWN (0x34b50)
keys = {
    0x34b50: "KEY_PAGEDOWN",
    0x34b5d: "KEY_PAGEUP",
    0x34b68: "KEY_BRIGHTNESSUP",
    0x34b79: "KEY_BRIGHTNESSDOWN",
    0x34b8c: "KEY_POWER",
    0x34c62: "KEY_SLEEP",
    0x34c6c: "KEY_WAKEUP",
}

for va, name in keys.items():
    pat = struct.pack("<I", va)
    pos = 0
    while True:
        pos = elf_data.find(pat, pos)
        if pos == -1:
            break
        ref_va = pos + 0x10000
        print(f"Pointer to {name} ({hex(va)}) at VA {hex(ref_va)}")
        # Check what is next to this pointer!
        # Maybe an evdev keycode integer?
        surrounding = elf_data[pos-8:pos+16]
        words = [hex(struct.unpack("<I", surrounding[w:w+4])[0]) for w in range(0, len(surrounding), 4)]
        print(f"  Surrounding words: {words}")
        pos += 4
