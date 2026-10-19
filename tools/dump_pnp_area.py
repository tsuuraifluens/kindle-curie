with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    app = f.read()
base = 0x01E00120
for off in range(0x223B0, 0x223E0):
    vaddr = base + off
    b = app[off]
    c = chr(b) if 32 <= b < 127 else '.'
    print(f'app off 0x{off:05X} (VADDR 0x{vaddr:08X}): 0x{b:02X} ({c})')
