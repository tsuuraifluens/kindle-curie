with open("binaries/input_manager", "rb") as f:
    elf_data = f.read()

import capstone
md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)
md.detail = True

# In PIC code on ARM (e.g. -fPIC or -fPIE):
# Typically:
# ldr rX, [pc, #offset] -> loads offset from GOT or PC
# add rX, pc
# Let's search for any 32-bit word in .text that, when added to its PC, gives ~0x319c5 or ~0x33e34!

targets = [0x319c5, 0x33de6, 0x33e34, 0x33e57, 0x33e84]

text_start = 0x18f50
text_end = 0x317f8
text_bytes = elf_data[text_start - 0x10000 : text_end - 0x10000]

import struct

print("Searching for relative offsets to targets...")
# Check 32-bit words at 4-byte boundaries
for off in range(0, len(text_bytes) - 4, 4):
    val = struct.unpack("<i", text_bytes[off:off+4])[0]
    word_va = text_start + off
    for t in targets:
        # Check word_va + val, or (word_va + 4) + val
        for delta in [0, 4, 8]:
            base = word_va + delta
            if base + val == t:
                print(f"Match: at {hex(word_va)}: val={hex(val)} + ({hex(word_va)}+{delta}) = {hex(t)}")
        # Also check if val is offset relative to GOT or base
        if val == t:
            print(f"Direct match at {hex(word_va)}: val={hex(val)}")
        if val == (t - 0x10000):
            print(f"Offset match at {hex(word_va)}: val={hex(val)}")
