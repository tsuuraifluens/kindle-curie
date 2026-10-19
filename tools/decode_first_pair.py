import struct

with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    data = f.read()
base = 0x01E00120

from decode_conn_event import decode_all
print('=== BLE Connection Event Handler (0x05460 - 0x05550) ===')
decode_all(0x05460, 0x05550)
