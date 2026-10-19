with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    app = f.read()

base = 0x01E00120

print("=== GATT ATT Database at 0x22050 ===")
off = 0x22050
while off < 0x22350:
    # Let's inspect each entry
    # Typically in JieLi:
    # 2 bytes size/flags
    # 2 bytes properties
    # 2 bytes handle
    # 2 bytes or 16 bytes UUID
    # ...
    chunk = app[off:off+16]
    print(f"0x{off:05X}: " + chunk.hex(' '))
    off += 16
