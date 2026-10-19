import sys
sys.path.insert(0, 'scripts')
import struct
from detailed_decode_74d0 import decode_one

app = open('extracted/ufw_unpacked/files/app.bin', 'rb').read()

off = 0xABB0
while off < 0xAC30:
    length, text = decode_one(off)
    b = app[off:off+length]
    print(f'0x{off:04X} ({b.hex(" ")}): {text}')
    off += length
