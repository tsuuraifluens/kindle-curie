import sys
sys.path.insert(0, 'scripts')
import struct
from decode_adv_loop import decode_ins

app = open('extracted/ufw_unpacked/files/app.bin', 'rb').read()
base = 0x01E00120

off = 0x4CE0
while off < 0x4D80:
    length, s = decode_ins(off)
    print(f'0x{off:04X}: {s}')
    off += length
