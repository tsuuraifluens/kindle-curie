import struct

with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    data = f.read()
base = 0x01E00120

from decode_conn_event import decode_all
print('=== SM Event Callback (0x02720 - 0x02880) ===')
decode_all(0x02720, 0x02880)
