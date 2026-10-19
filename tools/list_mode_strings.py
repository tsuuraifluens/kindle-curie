import struct

with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    app = f.read()

base_addr = 0x01E00120

# Let's inspect strings at 0x211C0 to 0x21350
off = 0x211C0
while off < 0x21350:
    st = app[off:off+30].split(b'\x00')[0]
    if len(st) > 2:
        print(f'String at 0x{off:05X} (CPU 0x{base_addr+off:08X}): "{st.decode(errors="ignore")}"')
        off += len(st) + 1
    else:
        off += 1
