import struct

app_path = r'extracted/ufw_unpacked/files/app.bin'
with open(app_path, 'rb') as f:
    app_data = f.read()

base_addr = 0x01E00120

# Search for all strings referenced between 0xE400 and 0xEA00
for off in range(0xE400, 0xEA00):
    w0 = struct.unpack_from('<H', app_data, off)[0]
    if (w0 & 0xFFF0) == 0xFFC0:
        reg = w0 & 0xF
        imm = struct.unpack_from('<I', app_data, off+2)[0]
        if base_addr <= imm < base_addr + len(app_data):
            s = app_data[imm-base_addr:imm-base_addr+40].split(b'\x00')[0]
            print(f'0x{off:04X} (V:0x{base_addr+off:08X}): mov r{reg}, 0x{imm:08X} ; "{s.decode(errors="ignore")}"')
