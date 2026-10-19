import struct

app_path = r'extracted/ufw_unpacked/files/app.bin'
with open(app_path, 'rb') as f:
    app_data = f.read()

base_addr = 0x01E00120

print('=== Detailed byte dump at 0x7100 - 0x7150 ===')
for off in range(0x7100, 0x7150, 6):
    chunk = app_data[off:off+6]
    w0 = struct.unpack_from('<H', chunk, 0)[0]
    if (w0 & 0xFFF0) == 0xFFC0:
        reg = w0 & 0x000F
        imm = struct.unpack_from('<I', chunk, 2)[0]
        hint = ''
        if base_addr <= imm < base_addr + len(app_data):
            str_val = app_data[imm-base_addr:imm-base_addr+32].split(b'\x00')[0]
            if len(str_val) >= 2:
                hint = f' -> "{str_val.decode(errors="ignore")}"'
        print(f'0x{off:04X} (V:0x{base_addr+off:08X}): mov r{reg}, 0x{imm:08X}{hint}')
    else:
        print(f'0x{off:04X} (V:0x{base_addr+off:08X}): {chunk.hex(" ")}')
