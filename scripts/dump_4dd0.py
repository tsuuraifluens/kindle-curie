import struct

app = open('extracted/ufw_unpacked/files/app.bin', 'rb').read()
base = 0x01E00120

print("Hex bytes 0x4DD0 to 0x4E00:")
chunk = app[0x4DD0:0x4E00]
for i in range(0, len(chunk), 16):
    print(f'{0x4DD0+i:04X}: ' + ' '.join(f'{b:02X}' for b in chunk[i:i+16]))
