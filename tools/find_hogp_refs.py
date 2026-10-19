import struct

with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    data = f.read()

base = 0x01E00120

# Search for reference to 0x1F4E4 (string '[BLE_HOGP]report_id %d,err!!!')
target_vaddr = base + 0x1F4E4
packed = struct.pack('<I', target_vaddr)
print(f'Looking for refs to 0x{target_vaddr:08X}...')
for i in range(len(data)-4):
    if data[i:i+4] == packed:
        print(f'Found at 0x{i:05X} (V: 0x{base+i:08X})')

# Let's inspect the function around this offset
off = 0x4800 # let's search where ble_hogp functions are
# Let's search for references to string 'ble_hogp' at 0x1F29A
hogp_str_vaddr = base + 0x1F29A
packed_hogp = struct.pack('<I', hogp_str_vaddr)
for i in range(len(data)-4):
    if data[i:i+4] == packed_hogp:
        print(f'Found ble_hogp ref at 0x{i:05X} (V: 0x{base+i:08X})')
