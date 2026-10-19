import struct

with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    app = f.read()

base = 0x01E00120

# Let's inspect the caller of 0x16BC0 or find function entry
# Look backwards from 0x16BD0 for function prologue
print("Looking backwards from 0x16BD0:")
for p in range(0x16B80, 0x16BD0, 2):
    w = struct.unpack_from('<H', app, p)[0]
    print(f"0x{p:05X}: {w:04X} ({app[p:p+2].hex()})")
