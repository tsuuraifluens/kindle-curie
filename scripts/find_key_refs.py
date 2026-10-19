import struct

app_path = r'extracted/ufw_unpacked/files/app.bin'
with open(app_path, 'rb') as f:
    app_data = f.read()

base_addr = 0x01E00120

strings_to_check = [
    b'key.key:%x',
    b'preport.key :%x',
    b'remap_key.consumer_key',
    b'getkey: %s',
    b'IINE_reader'
]

for s in strings_to_check:
    off = app_data.find(s)
    if off != -1:
        vaddr = base_addr + off
        print(f'String: {repr(s)} at off 0x{off:05X} (V: 0x{vaddr:08X})')
        # find pointers
        ptr = struct.pack('<I', vaddr)
        refs = [hex(m) for m in range(len(app_data)-4) if app_data[m:m+4] == ptr]
        print(f'  Referenced at: {refs}')
