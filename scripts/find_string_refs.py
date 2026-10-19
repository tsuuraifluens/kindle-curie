import struct

app_path = r'extracted/ufw_unpacked/files/app.bin'
with open(app_path, 'rb') as f:
    app_data = f.read()

# Let's search where string "IINE_reader" (at offset 0x211C6) is referenced!
# Note: app.bin entry point is 0x01E00120. In JieLi AC632N/bd19, flash base is typically 0x01E00000 or similar.
# Let's find potential base addresses by searching for pointers to strings!

str_off = app_data.find(b'IINE_reader\x00')
print(f'IINE_reader string offset in app.bin: 0x{str_off:05X}')

# Let's search 4-byte integers in app.bin that point to str_off + base
# Common base addresses: 0x01E00000, 0x01E00120, 0x00000000, 0x08000000
for base in [0x01E00000, 0x01E00120, 0x01E00020, 0x01E02000, 0x01E02120, 0x00000000]:
    target_ptr = base + str_off
    ptr_bytes_le = struct.pack('<I', target_ptr)
    ptr_bytes_be = struct.pack('>I', target_ptr)
    
    matches_le = [m for m in range(0, len(app_data)-4) if app_data[m:m+4] == ptr_bytes_le]
    matches_be = [m for m in range(0, len(app_data)-4) if app_data[m:m+4] == ptr_bytes_be]
    
    if matches_le:
        print(f'Base 0x{base:08X} (LE): matches at {[hex(x) for x in matches_le]}')
    if matches_be:
        print(f'Base 0x{base:08X} (BE): matches at {[hex(x) for x in matches_be]}')
