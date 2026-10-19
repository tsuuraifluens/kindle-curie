import struct

app_path = r'extracted/ufw_unpacked/files/app.bin'
with open(app_path, 'rb') as f:
    app_data = f.read()

base_addr = 0x01E00120

print('=== Code around 0xE5E2 (preport.key) ===')
start = 0xE5A0
end = 0xE650
for off in range(start, end, 16):
    chunk = app_data[off:off+16]
    print(f'0x{off:04X} (0x{base_addr+off:08X}): {chunk.hex(" ")}')

print('\n=== Code around 0xE7B6 (remap_key.consumer_key) ===')
start = 0xE760
end = 0xE850
for off in range(start, end, 16):
    chunk = app_data[off:off+16]
    print(f'0x{off:04X} (0x{base_addr+off:08X}): {chunk.hex(" ")}')
