import struct

app = open('extracted/ufw_unpacked/files/app.bin', 'rb').read()
base = 0x01E00120

print("Bytes around 0x2DF70 to 0x2DFB0:")
chunk = app[0x2DF70:0x2DFA0]
for i in range(0, len(chunk), 16):
    print(f'{0x2DF70+i:05X}: ' + ' '.join(f'{b:02X}' for b in chunk[i:i+16]))

# Also disassemble around 0x2DF8C
off = 0x2DF70
while off < 0x2DFB0:
    w0 = struct.unpack_from('<H', app, off)[0]
    if (w0 & 0xFFF0) == 0xFFC0:
        reg = w0 & 0xF
        imm = struct.unpack_from('<I', app, off+2)[0]
        print(f'0x{off:04X}: mov r{reg}, 0x{imm:08X}')
        off += 6
        continue
    print(f'0x{off:04X}: 0x{w0:04X}')
    off += 2
