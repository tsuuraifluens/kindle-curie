import sys
sys.path.insert(0, 'scripts')
from decode_adv_loop import decode_ins

app = open('extracted/ufw_unpacked/files/app.bin', 'rb').read()

# Let's decode from 0x4DF0 to 0x4ED0 (name and scan_rsp building)
off = 0x4DFA
while off < 0x4EA0:
    l, t = decode_ins(off)
    print(f'0x{off:04X}: {t}')
    off += l
