import struct

with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    data = f.read()
base = 0x01E00120

from decode_conn_event import decode_all
print('=== Mode Switch Function (0x07100 - 0x07250) ===')
decode_all(0x07100, 0x07250)
