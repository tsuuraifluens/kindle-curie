import struct

with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    app = f.read()

base = 0x01E00120

print("Bytes around 0x220B0 to 0x22120:")
chunk = app[0x220B0:0x22120]
for i in range(0, len(chunk), 16):
    print(f'{0x220B0+i:05X}: ' + ' '.join(f'{b:02X}' for b in chunk[i:i+16]))
