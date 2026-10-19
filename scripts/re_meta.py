import struct, re

with open('extracted/updater_unpacked.exe', 'rb') as f:
    data = f.read()

# Let's search for setFileCrc in data
methods = [
    b'setFileCrc',
    b'firmwareCrcChanged',
    b'firmwarePidChanged',
    b'firmwareVidChanged',
    b'firmwareContentNotify',
]

for m in methods:
    pos = 0
    while True:
        pos = data.find(m, pos)
        if pos == -1: break
        print(f"Method string '{m.decode()}' at 0x{pos:X}")
        pos += len(m)

# Let's find references to the string offsets in the binary
# Code VA base = 0x400000, .rdata VA = 0xD29000 (offset 0x927A00)
# Let's map offset to VA
def off_to_va(off):
    # .text is 0x400 .. 0x927A00 -> VA 0x401000 + (off - 0x400)
    # .rdata is 0x927A00 .. 0xD39400 -> VA 0xD29000 + (off - 0x927A00)
    # .data is 0xD39400 .. 0xD55E00 -> VA 0x113B000 + (off - 0xD39400)
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

def find_ptrs_to(va):
    target = struct.pack('<I', va)
    pos = 0
    res = []
    while True:
        pos = data.find(target, pos)
        if pos == -1: break
        res.append((pos, off_to_va(pos)))
        pos += 4
    return res

# Let's see QMetaObject for jl_file_edit
pos = data.find(b'jl_file_edit\x00')
if pos != -1:
    print(f"jl_file_edit string at 0x{pos:X}, VA: 0x{off_to_va(pos):X}")
    ptrs = find_ptrs_to(off_to_va(pos))
    print(f"Pointers to jl_file_edit string: {[hex(p[0]) for p in ptrs]}")
