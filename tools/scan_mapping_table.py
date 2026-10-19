with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    data = f.read()

print('=== Scanning entries of 6 bytes from 0x2289C to 0x22C00 ===')
for off in range(0x2289C, 0x22C00, 6):
    chunk = data[off:off+6]
    if any(chunk):
        idx = (off - 0x2289C) // 6
        h = chunk.hex(' ')
        print(f"0x{off:05X} (idx {idx:3d}): {h}")
