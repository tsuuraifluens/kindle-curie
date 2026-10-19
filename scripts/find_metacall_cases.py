import struct

with open('extracted/updater_unpacked.exe', 'rb') as f:
    data = f.read()

def off_to_va(off):
    if 0x400 <= off < 0x927A00:
        return 0x401000 + (off - 0x400)
    elif 0x927A00 <= off < 0xD39400:
        return 0xD29000 + (off - 0x927A00)
    elif 0xD39400 <= off < 0xD55E00:
        return 0x113B000 + (off - 0xD39400)
    return None

def va_to_off(va):
    if 0x401000 <= va < 0xD29000:
        return 0x400 + (va - 0x401000)
    elif 0xD29000 <= va < 0x113B000:
        return 0x927A00 + (va - 0xD29000)
    elif 0x113B000 <= va < 0x1166000:
        return 0xD39400 + (va - 0x113B000)
    return None

# static_metacall is at VA 0x004235F0 -> file offset 0x229F0
# Let's inspect the first 256 bytes of static_metacall
code = data[0x229F0 : 0x22DF0]
print("static_metacall code (hex):")
print(code[:100].hex(' '))

# In x86, switch is often implemented as:
# cmp eax, 33 / ja def / jmp dword ptr [eax*4 + jumptable]
# Let's find any jump table pointers in this function
for i in range(len(code) - 4):
    val = struct.unpack_from('<I', code, i)[0]
    # Check if val is a valid VA in .rdata or .text
    off = va_to_off(val)
    if off and (0x927A00 <= off < 0xD39400 or 0x400 <= off < 0x927A00):
        # Check if it looks like a table of 34 function/code addresses
        table_bytes = data[off : off + 34 * 4]
        if len(table_bytes) == 34 * 4:
            vas = struct.unpack_from('<34I', data, off)
            # Check if all vas are code pointers in .text (0x401000..0xD29000)
            if all(0x401000 <= v < 0xD29000 for v in vas):
                print(f"FOUND JUMP TABLE at VA 0x{val:08X} (file offset 0x{off:X}) referenced at 0x{0x229F0+i:X}")
                for idx, v in enumerate(vas):
                    print(f"  case {idx}: VA 0x{v:08X} (file offset 0x{va_to_off(v):X})")
