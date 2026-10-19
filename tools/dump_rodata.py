with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

def dump_strings(start_va, length):
    start_off = start_va - 0x10000
    sub = elf_data[start_off:start_off + length]
    # split by null bytes
    parts = sub.split(b"\x00")
    cur_va = start_va
    for p in parts:
        if p:
            try:
                print(f"VA {hex(cur_va)}: {p.decode('utf-8')}")
            except:
                print(f"VA {hex(cur_va)}: {p}")
        cur_va += len(p) + 1

print("--- Strings around 0x319d8 (categories.json) ---")
dump_strings(0x31950, 200)

print("\n--- Strings around 0x33db8 (display.brightness / system.sleep) ---")
dump_strings(0x33da0, 250)

print("\n--- Strings around 0x34b50 (KEY_POWER / KEY_WAKEUP / KEY_SLEEP) ---")
dump_strings(0x34b50, 400)
