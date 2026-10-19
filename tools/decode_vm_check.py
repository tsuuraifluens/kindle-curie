import struct

with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    data = f.read()
base = 0x01E00120

from decode_conn_event import decode_all
print('=== AFTER syscfg_read in adv_setup_init (0x04F3C - 0x04FC0) ===')
decode_all(0x04F3C, 0x04FC0)
