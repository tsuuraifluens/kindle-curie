with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

import struct

strings_to_search = [
    b"system.sleep",
    b"display.brightness_up",
    b"display.brightness_down",
    b"KEY_POWER",
    b"KEY_WAKEUP",
    b"KEY_SLEEP",
    b"setWakeUpAsync",
    b"setPowerButtonAsync",
    b"setPowerButtonIfActiveAsync",
    b"powerd",
    b"categories.json",
    b"def:action:sleep",
]

str_addrs = {}
for s in strings_to_search:
    pos = 0
    while True:
        pos = elf_data.find(s, pos)
        if pos == -1:
            break
        va = pos + 0x10000
        print(f"String '{s.decode(errors='ignore')}' found at VA {hex(va)} (file offset {hex(pos)})")
        str_addrs[va] = s
        pos += len(s)

print("\n--- Cross-referencing pointers to these strings ---")
for va, s in str_addrs.items():
    pat = struct.pack("<I", va)
    pos = 0
    found = False
    while True:
        pos = elf_data.find(pat, pos)
        if pos == -1:
            break
        ref_va = pos + 0x10000
        print(f"Pointer to '{s.decode(errors='ignore')}' (VA {hex(va)}) at VA {hex(ref_va)}")
        pos += 4
        found = True
