import sys
sys.path.insert(0, 'scripts')
from detailed_decode_74d0 import decode_one

app = open('extracted/ufw_unpacked/files/app.bin', 'rb').read()

off = 0x4D40
while off < 0x4D80:
    l, t = decode_one(off)
    b = app[off:off+l]
    print(f'0x{off:04X} ({b.hex(" ")}): {t}')
    off += l
