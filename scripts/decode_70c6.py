import sys
sys.path.insert(0, 'scripts')
from detailed_decode_74d0 import decode_one

app = open('extracted/ufw_unpacked/files/app.bin', 'rb').read()

off = 0x70C6
while off < 0x7190:
    l, t = decode_one(off)
    print(f'0x{off:04X}: {t}')
    off += l
