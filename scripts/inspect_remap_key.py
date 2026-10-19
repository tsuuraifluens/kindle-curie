import struct

app_path = r'extracted/ufw_unpacked/files/app.bin'
with open(app_path, 'rb') as f:
    app_data = f.read()

base_addr = 0x01E00120

print('=== Disasm around remap_key.consumer_key (0xE760 - 0xE7E0) ===')
# Let's inspect all 16-bit words
for off in range(0xE760, 0xE7E0, 2):
    w = struct.unpack_from('<H', app_data, off)[0]
    # Check if mov reg, imm32
    if (w & 0xFFF0) == 0xFFC0:
        reg = w & 0xF
        imm = struct.unpack_from('<I', app_data, off+2)[0]
        s = ''
        if base_addr <= imm < base_addr + len(app_data):
            st = app_data[imm-base_addr:imm-base_addr+30].split(b'\x00')[0]
            s = f' ; "{st.decode(errors="ignore")}"'
        print(f'0x{off:04X}: mov r{reg}, 0x{imm:08X}{s}')
    else:
        print(f'0x{off:04X}: {w:04X} ({w:016b})')
