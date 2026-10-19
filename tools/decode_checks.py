import struct

with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    data = f.read()
base = 0x01E00120

from decode_conn_event import decode_all
print('=== Function at 0x16EE4 ===')
decode_all(0x16EE4, 0x16F30)

print('\n=== Function at 0x16A48 ===')
decode_all(0x16A48, 0x16A90)
