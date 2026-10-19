import struct

with open('extracted/updater_unpacked.exe', 'rb') as f:
    data = f.read()

def va_to_off(va):
    return 0x400 + (va - 0x401000)

off = va_to_off(0x0040E96C)
for i in range(0, 0x110, 16):
    va = 0x0040E96C + i
    chunk = data[off+i:off+i+16]
    print(f'0x{va:08X}: {" ".join(f"{b:02x}" for b in chunk)}')
