import struct

app = open('extracted/ufw_unpacked/files/app.bin', 'rb').read()
base = 0x01E00120

for addr in range(0x01E224B0, 0x01E22500, 4):
    # search addr in app
    needle = struct.pack('<I', addr)
    pos = 0
    while True:
        pos = app.find(needle, pos)
        if pos == -1: break
        print(f'Addr 0x{addr:08X} (offset 0x{addr-base:05X}) referenced at offset 0x{pos:05X} (VADDR 0x{base+pos:08X})')
        pos += 1
