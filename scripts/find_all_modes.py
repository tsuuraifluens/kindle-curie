import struct

app_path = r'extracted/ufw_unpacked/files/app.bin'
with open(app_path, 'rb') as f:
    app_data = f.read()

base_addr = 0x01E00120

modes = [
    'IINE_reader',
    'Xbox Wireless Controller',
    'IINE_keyboard',
    'IINE_mouse',
    'IINE_phone',
    'IINE_control'
]

for name in modes:
    off = app_data.find(name.encode() + b'\x00')
    vaddr = base_addr + off
    print(f'Mode: {name:<26} Offset: 0x{off:05X} (V: 0x{vaddr:08X})')
    # find references
    ptr = struct.pack('<I', vaddr)
    refs = [hex(m) for m in range(len(app_data)-4) if app_data[m:m+4] == ptr]
    print(f'   Referenced at: {refs}')
