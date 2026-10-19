import struct

app_path = r'extracted/ufw_unpacked/files/app.bin'
with open(app_path, 'rb') as f:
    app_data = f.read()

base_addr = 0x01E00120

print('Inspecting data around 0x7130 (virtual addr 0x{:08X}):'.format(base_addr + 0x7130))

# Dump 32-bit words and check if they are pointers to strings or tables
start = 0x70E0
end = 0x7200

for off in range(start, end, 16):
    words = struct.unpack_from('<4I', app_data, off)
    line = f'0x{off:04X} (V:0x{base_addr+off:08X}): '
    line += ' '.join(f'{w:08X}' for w in words)
    
    # Check if any word is a pointer to a string in app.bin
    str_hints = []
    for w in words:
        if base_addr <= w < base_addr + len(app_data):
            target_off = w - base_addr
            # Check if there is an ascii string there
            m = app_data[target_off:target_off+32].split(b'\x00')[0]
            if len(m) >= 2 and all(32 <= b <= 126 for b in m):
                str_hints.append(f'0x{w:08X}->" {m.decode()} "')
    if str_hints:
        line += '  |  ' + ', '.join(str_hints)
    print(line)
