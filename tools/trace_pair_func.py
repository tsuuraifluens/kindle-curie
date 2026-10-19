import struct

with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
    data = f.read()
base = 0x01E00120

# Search backwards from 0x18798 for function prologue:
# In q32s: function prologue is usually:
# [sp - framesize] or mov [sp + ...], r...
# Let's inspect instructions from 0x18680 to 0x187A0
from decode_conn_event import decode_all
print('=== 0x18680 to 0x187B0 ===')
decode_all(0x18680, 0x187B0)
