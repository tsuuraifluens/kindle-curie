import struct

with open(r'extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    app_data = f.read()

base_addr = 0x01E00120

print('=== Code around 0x4496 ([BLE_HOGP]read PnP_ID) ===')
for off in range(0x4460, 0x44F0, 2):
    chunk = app_data[off:off+6]
    w = struct.unpack_from('<H', chunk, 0)[0]
    if (w & 0xFFF0) == 0xFFC0:
        reg = w & 0xF
        imm = struct.unpack_from('<I', chunk, 2)[0]
        s = ''
        if base_addr <= imm < base_addr + len(app_data):
            st = app_data[imm-base_addr:imm-base_addr+30].split(b'\x00')[0]
            s = f' ; "{st.decode(errors="ignore")}"'
        print(f'0x{off:04X}: mov r{reg}, 0x{imm:08X}{s}')
    else:
        print(f'0x{off:04X}: {w:04X}')
