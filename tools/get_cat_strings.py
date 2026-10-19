with open("binaries/libAccessories.so", "rb") as f:
    elf_data = f.read()

import struct

start_va = 0x353fc
end_va = 0x358a0

# Scan 32-bit words in literal pool at end of function
for off in range(start_va, end_va, 4):
    val = struct.unpack("<i", elf_data[off:off+4])[0]
    target = off + val
    if 0 <= target < len(elf_data):
        end = elf_data.find(b"\x00", target)
        if end != -1 and 2 < end - target < 200:
            s = elf_data[target:end]
            if all(32 <= b < 127 for b in s):
                print(f"Literal at {hex(off)} -> target {hex(target)}: {s.decode()}")
