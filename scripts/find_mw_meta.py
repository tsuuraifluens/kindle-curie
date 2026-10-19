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

# The stringdata for MainWindow has QByteArrayData entries.
# Each entry is 24 bytes (in 32-bit: qptrdiff ref = -1, int size, uint alloc, qptrdiff offset)
# Let's see the entries before "MainWindow":
# "MainWindow" is at 0x9C6440.
# Let's inspect bytes before 0x9C6440:
print("Stringdata headers before MainWindow:")
pos = 0x9C6440
# Count strings in MainWindow meta:
# Let's parse all strings in that block:
strings = []
curr = pos
end = data.find(b'\x00\x00\x00\x00', curr) # or look at strings separated by 0
# Let's print the string table
str_bytes = data[0x9C6440 : 0x9C6800]
parts = str_bytes.split(b'\x00')
for i, p in enumerate(parts[:35]):
    if p:
        print(f"String [{i}]: {p.decode('latin1', errors='replace')}")

# Now let's find qt_meta_data_MainWindow (uint array)
# It starts with:
# 7, // revision
# 0, // classname (index 0)
# ...
# Let's search for the pattern of ints: [7, 0, ...]
idx = 0
while True:
    idx = data.find(struct.pack('<II', 7, 0), idx)
    if idx == -1: break
    # Check if next ints look like class info / method counts
    ints = struct.unpack_from('<10I', data, idx)
    # in Qt5: revision(7), classname(0), classinfo_count, classinfo_data, method_count, method_data...
    # method_count should be around 25-30
    if ints[4] in range(15, 40):
        print(f"Candidate qt_meta_data at 0x{idx:X} (VA 0x{off_to_va(idx):X}): {ints}")
    idx += 4
